#!/usr/bin/env python3
"""
DiaKG → 纯文本导出 / Milvus 导入（一期：doc_type=guideline）

用法见 docs/Milvus医学知识库落地指南.md
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import sys
import time
from pathlib import Path
from typing import Any

import requests

# 一期固定：DiaKG 诊疗指南
PHASE1_DOC_TYPE = "guideline"
DEFAULT_COLLECTION = "diabetes_knowledge"
DEFAULT_OUTPUT_DIR = "diakg_text"


def project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip())


def extract_plain_text(json_path: Path) -> tuple[str, str, list[str]]:
    """方式 A：提取 title 与正文段落（不含首段标题）。"""
    data = json.loads(json_path.read_text(encoding="utf-8"))
    doc_id = str(data.get("doc_id", json_path.stem))
    paragraphs = [
        p["paragraph"].strip()
        for p in data.get("paragraphs", [])
        if p.get("paragraph", "").strip()
    ]
    if not paragraphs:
        title = f"doc_{doc_id}"
        return doc_id, title, []
    title = paragraphs[0]
    title = normalize_title(title, doc_id)
    body = paragraphs[1:] if len(paragraphs) > 1 else []
    return doc_id, title, body


def merge_chunks(paragraphs: list[str], target_size: int = 500, overlap: int = 80) -> list[str]:
    if not paragraphs:
        return []
    chunks: list[str] = []
    buf: list[str] = []
    size = 0
    for para in paragraphs:
        extra = len(para) + (2 if buf else 0)
        if size + extra > target_size and buf:
            chunks.append("\n\n".join(buf))
            tail = buf[-1]
            buf = [tail, para] if len(tail) <= overlap else [para]
            size = sum(len(x) for x in buf) + 2 * max(len(buf) - 1, 0)
        else:
            buf.append(para)
            size += extra
    if buf:
        chunks.append("\n\n".join(buf))
    return chunks


def iter_json_files(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    files = list(input_path.glob("*.json"))
    return sorted(files, key=lambda p: int(p.stem) if p.stem.isdigit() else p.stem)


def build_txt_content(title: str, body: list[str]) -> str:
    if body:
        return f"# {title}\n\n" + "\n\n".join(body)
    return f"# {title}\n"


def export_txt_files(input_path: Path, output_dir: Path) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest: list[dict[str, Any]] = []
    for json_file in iter_json_files(input_path):
        doc_id, title, body = extract_plain_text(json_file)
        rel_source = f"diakg/{json_file.name}"
        txt_path = output_dir / f"{doc_id}.txt"
        txt_path.write_text(build_txt_content(title, body), encoding="utf-8")
        manifest.append(
            {
                "doc_id": doc_id,
                "doc_title": title,
                "doc_source": rel_source,
                "doc_type": PHASE1_DOC_TYPE,
                "txt_file": str(txt_path.relative_to(project_root())).replace("\\", "/"),
                "paragraph_count": len(body) + (1 if title else 0),
                "char_count": len(txt_path.read_text(encoding="utf-8")),
            }
        )
        print(f"  exported {json_file.name} -> {txt_path.name} ({len(body)} body paragraphs)")
    manifest_path = output_dir / "manifest.json"
    summary = {
        "phase": 1,
        "doc_type": PHASE1_DOC_TYPE,
        "exported_at": int(time.time()),
        "total_files": len(manifest),
        "documents": manifest,
    }
    manifest_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"manifest -> {manifest_path}")
    return summary


def truncate_utf8(text: str, max_bytes: int) -> str:
    raw = text.encode("utf-8")
    if len(raw) <= max_bytes:
        return text
    cut = raw[:max_bytes]
    while cut:
        try:
            return cut.decode("utf-8")
        except UnicodeDecodeError:
            cut = cut[:-1]
    return ""


def normalize_title(title: str, doc_id: str, max_chars: int = 120) -> str:
    title = title.strip()
    if not title:
        return f"doc_{doc_id}"
    if len(title) > max_chars:
        return title[: max_chars - 1] + "…"
    return title


def chunk_id(doc_id: str, chunk_index: int) -> str:
    return f"diakg_{doc_id}_{chunk_index}"


class EmbeddingClient:
    def __init__(
        self,
        provider: str,
        base_url: str,
        api_key: str,
        model: str,
        dimension: int,
    ) -> None:
        self.provider = provider.lower()
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.dimension = dimension

    def embed(self, text: str) -> list[float]:
        return self.embed_batch([text])[0]

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if self.provider == "local":
            return [self._embed_local_hash(t) for t in texts]
        return self._embed_openai_compatible_batch(texts)

    def _embed_openai_compatible_batch(self, texts: list[str]) -> list[list[float]]:
        resp = requests.post(
            f"{self.base_url}/v1/embeddings",
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={"model": self.model, "input": texts},
            timeout=300,
        )
        resp.raise_for_status()
        items = resp.json()["data"]
        if len(items) != len(texts):
            raise ValueError(f"Embedding 返回数量 {len(items)} != 请求 {len(texts)}")
        vectors: list[list[float]] = []
        for item in items:
            vec = item["embedding"]
            if len(vec) != self.dimension:
                raise ValueError(f"Embedding 维度 {len(vec)} != 期望 {self.dimension}")
            vectors.append(_normalize(vec))
        return vectors

    def _embed_openai_compatible(self, text: str) -> list[float]:
        return self._embed_openai_compatible_batch([text])[0]

    def _embed_local_hash(self, text: str) -> list[float]:
        vec = [0.0] * self.dimension
        tokens = re.split(r"[\s,，、；;。.!！?？]+", text.lower())
        for token in tokens:
            if len(token) <= 1:
                continue
            h = hash(token)
            for i in range(4):
                idx = (h + i * 9973) % self.dimension
                vec[idx] += 1.0
        return _normalize(vec)


def _normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(v * v for v in vec))
    if norm <= 0:
        return vec
    return [v / norm for v in vec]


def _cosine_similarity(distance: float) -> float:
    return max(0.0, min(1.0, 1.0 - distance))


class MilvusKnowledgeStore:
    def __init__(
        self,
        host: str,
        port: int,
        collection_name: str,
        dimension: int,
        index_nlist: int = 128,
    ) -> None:
        from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility

        self._Collection = Collection
        self._utility = utility
        self.collection_name = collection_name
        self.dimension = dimension
        self.index_nlist = index_nlist

        alias = "default"
        connections.connect(alias=alias, host=host, port=port)
        if not utility.has_collection(collection_name):
            fields = [
                FieldSchema(name="id", dtype=DataType.VARCHAR, is_primary=True, max_length=64),
                FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=dimension),
                FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
                FieldSchema(name="doc_title", dtype=DataType.VARCHAR, max_length=256),
                FieldSchema(name="doc_source", dtype=DataType.VARCHAR, max_length=256),
                FieldSchema(name="doc_type", dtype=DataType.VARCHAR, max_length=64),
                FieldSchema(name="page_num", dtype=DataType.INT64),
                FieldSchema(name="chunk_index", dtype=DataType.INT64),
                FieldSchema(name="created_at", dtype=DataType.INT64),
            ]
            schema = CollectionSchema(fields, description="糖尿病医学知识库（一期 DiaKG guideline）")
            col = Collection(collection_name, schema)
            col.create_index(
                field_name="vector",
                index_params={
                    "index_type": "IVF_FLAT",
                    "metric_type": "COSINE",
                    "params": {"nlist": index_nlist},
                },
            )
            print(f"created collection [{collection_name}]")
        self.col = Collection(collection_name)
        self.col.load()

    def upsert_chunks(self, rows: list[dict[str, Any]]) -> None:
        if not rows:
            return
        self.col.upsert(rows)

    def upsert_chunk(
        self,
        chunk_pk: str,
        vector: list[float],
        content: str,
        doc_title: str,
        doc_source: str,
        doc_type: str,
        chunk_index: int,
        created_at: int,
        page_num: int = 0,
    ) -> None:
        self.col.upsert(
            [
                {
                    "id": chunk_pk,
                    "vector": vector,
                    "content": truncate_utf8(content, 65535),
                    "doc_title": truncate_utf8(doc_title, 256),
                    "doc_source": truncate_utf8(doc_source, 256),
                    "doc_type": truncate_utf8(doc_type, 64),
                    "page_num": page_num,
                    "chunk_index": chunk_index,
                    "created_at": created_at,
                }
            ]
        )

    def flush(self) -> None:
        self.col.flush()

    def count(self) -> int:
        self.col.flush()
        return int(self.col.num_entities)

    def delete_by_source(self, doc_source: str) -> None:
        escaped = doc_source.replace("\\", "\\\\").replace('"', '\\"')
        self.col.delete(expr=f'doc_source == "{escaped}"')
        self.col.flush()

    def search(
        self,
        vector: list[float],
        top_k: int,
        doc_type: str | None = None,
        score_threshold: float = 0.0,
    ) -> list[dict[str, Any]]:
        expr = f'doc_type == "{doc_type}"' if doc_type else None
        results = self.col.search(
            data=[vector],
            anns_field="vector",
            param={"metric_type": "COSINE", "params": {"nprobe": 16}},
            limit=top_k,
            expr=expr,
            output_fields=["content", "doc_title", "doc_source", "doc_type", "chunk_index"],
        )
        hits: list[dict[str, Any]] = []
        for hit in results[0]:
            score = _cosine_similarity(float(hit.distance))
            if score < score_threshold:
                continue
            entity = hit.entity
            hits.append(
                {
                    "id": hit.id,
                    "score": score,
                    "content": entity.get("content"),
                    "doc_title": entity.get("doc_title"),
                    "doc_source": entity.get("doc_source"),
                    "doc_type": entity.get("doc_type"),
                    "chunk_index": entity.get("chunk_index"),
                }
            )
        return hits


def build_knowledge_context(hits: list[dict[str, Any]]) -> str:
    blocks: list[str] = []
    for i, hit in enumerate(hits, start=1):
        title = hit.get("doc_title") or "未知来源"
        score = hit.get("score", 0.0)
        content = (hit.get("content") or "").strip()
        blocks.append(f"【片段{i} | 来源: {title} | 相似度: {score:.3f}】\n{content}")
    return "\n\n".join(blocks)


def import_diakg(
    input_path: Path,
    store: MilvusKnowledgeStore,
    embedder: EmbeddingClient,
    chunk_size: int,
    chunk_overlap: int,
    batch_size: int = 8,
) -> dict[str, Any]:
    created_at = int(time.time())
    total_chunks = 0
    pending_texts: list[str] = []
    pending_rows: list[dict[str, Any]] = []

    def flush_batch() -> None:
        nonlocal total_chunks
        if not pending_texts:
            return
        vectors = embedder.embed_batch(pending_texts)
        rows = []
        for vec, meta in zip(vectors, pending_rows):
            rows.append({**meta, "vector": vec})
        store.upsert_chunks(rows)
        total_chunks += len(rows)
        first_pk = pending_rows[0]["id"]
        last_pk = pending_rows[-1]["id"]
        print(f"  upsert batch {len(rows)} ({first_pk} .. {last_pk})", flush=True)
        pending_texts.clear()
        pending_rows.clear()

    for json_file in iter_json_files(input_path):
        doc_id, title, body = extract_plain_text(json_file)
        rel_source = f"diakg/{json_file.name}"
        paragraphs = body if body else ([title] if title else [])
        chunks = merge_chunks(paragraphs, target_size=chunk_size, overlap=chunk_overlap)
        if not chunks:
            print(f"  skip empty document: {json_file.name}", flush=True)
            continue
        print(f"  document {json_file.name}: {len(chunks)} chunks", flush=True)
        for idx, chunk in enumerate(chunks):
            pending_texts.append(chunk)
            pending_rows.append(
                {
                    "id": chunk_id(doc_id, idx),
                    "content": truncate_utf8(chunk, 65535),
                    "doc_title": truncate_utf8(title, 256),
                    "doc_source": truncate_utf8(rel_source, 256),
                    "doc_type": PHASE1_DOC_TYPE,
                    "page_num": 0,
                    "chunk_index": idx,
                    "created_at": created_at,
                }
            )
            if len(pending_texts) >= batch_size:
                flush_batch()
        flush_batch()
    store.flush()
    return {
        "phase": 1,
        "doc_type": PHASE1_DOC_TYPE,
        "chunks_upserted": total_chunks,
        "collection_entities": store.count(),
    }


def resolve_defaults(args: argparse.Namespace) -> argparse.Namespace:
    root = project_root()
    load_dotenv(root / ".env")
    if args.input is None:
        args.input = str(root / "diakg")
    if args.output is None:
        args.output = str(root / DEFAULT_OUTPUT_DIR)
    if args.host is None:
        args.host = os.environ.get("MILVUS_HOST", "localhost")
    if args.port is None:
        args.port = int(os.environ.get("MILVUS_PORT", "19530"))
    if args.collection is None:
        args.collection = DEFAULT_COLLECTION
    if args.dim is None:
        args.dim = int(os.environ.get("MILVUS_DIMENSION", "1024"))
    if args.embed_url is None:
        args.embed_url = os.environ.get("EMBEDDING_BASE_URL", "http://localhost:11434")
    if args.embed_model is None:
        args.embed_model = os.environ.get("EMBEDDING_MODEL", "qwen3-embedding:0.6b")
    if args.embed_key is None:
        args.embed_key = os.environ.get("EMBEDDING_API_KEY", "ollama")
    if args.embed_provider is None:
        provider = os.environ.get("EMBEDDING_PROVIDER", "openai")
        args.embed_provider = "local" if provider.lower() == "local" else "openai"
    return args


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="DiaKG 纯文本导出与 Milvus 导入（一期 guideline）")
    parser.add_argument("--input", help="DiaKG JSON 文件或目录（默认 diakg/）")
    parser.add_argument("--output", help=f"TXT 导出目录（默认 {DEFAULT_OUTPUT_DIR}/）")
    parser.add_argument("--export-only", action="store_true", help="仅导出 TXT，不写 Milvus")
    parser.add_argument("--import-only", action="store_true", help="仅导入 Milvus，跳过 TXT 导出")
    parser.add_argument("--batch-size", type=int, default=8, help="Embedding / upsert 批大小")
    parser.add_argument("--collection", default=None, help=f"Milvus collection（默认 {DEFAULT_COLLECTION}）")
    parser.add_argument("--host", default=None, help="Milvus host")
    parser.add_argument("--port", type=int, default=None, help="Milvus port")
    parser.add_argument("--dim", type=int, default=None, help="向量维度")
    parser.add_argument("--chunk-size", type=int, default=500, help="合并 chunk 目标字数")
    parser.add_argument("--chunk-overlap", type=int, default=80, help="chunk 重叠字数")
    parser.add_argument("--embed-provider", choices=["openai", "local"], default=None, help="Embedding 提供方")
    parser.add_argument("--embed-url", default=None, help="OpenAI 兼容 Embedding URL")
    parser.add_argument("--embed-model", default=None, help="Embedding 模型名")
    parser.add_argument("--embed-key", default=None, help="Embedding API Key")
    parser.add_argument("--search", metavar="QUERY", help="检索验证")
    parser.add_argument("--top-k", type=int, default=5, help="检索 Top-K")
    parser.add_argument("--score-threshold", type=float, default=0.0, help="相似度阈值")
    parser.add_argument(
        "--delete-by-source",
        metavar="DOC_SOURCE",
        help='按 doc_source 删除，如 diakg/1.json',
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = resolve_defaults(parser.parse_args())
    input_path = Path(args.input)

    if not input_path.exists():
        print(f"input not found: {input_path}", file=sys.stderr)
        return 1

    if args.delete_by_source:
        store = MilvusKnowledgeStore(
            host=args.host,
            port=args.port,
            collection_name=args.collection,
            dimension=args.dim,
        )
        store.delete_by_source(args.delete_by_source)
        print(f"deleted chunks where doc_source == {args.delete_by_source!r}")
        print(f"collection entities: {store.count()}")
        return 0

    if args.search:
        embedder = EmbeddingClient(
            provider=args.embed_provider,
            base_url=args.embed_url,
            api_key=args.embed_key,
            model=args.embed_model,
            dimension=args.dim,
        )
        store = MilvusKnowledgeStore(
            host=args.host,
            port=args.port,
            collection_name=args.collection,
            dimension=args.dim,
        )
        hits = store.search(
            vector=embedder.embed(args.search),
            top_k=args.top_k,
            doc_type=PHASE1_DOC_TYPE,
            score_threshold=args.score_threshold,
        )
        if not hits:
            print("no hits")
            return 0
        print(build_knowledge_context(hits))
        return 0

    output_dir = Path(args.output)
    if not args.import_only:
        print(f"=== export TXT -> {output_dir} (doc_type={PHASE1_DOC_TYPE}) ===", flush=True)
        summary = export_txt_files(input_path, output_dir)
        print(f"exported {summary['total_files']} files", flush=True)

    if args.export_only:
        return 0

    print(f"=== import Milvus collection={args.collection} (doc_type={PHASE1_DOC_TYPE}) ===", flush=True)
    if args.embed_provider == "local":
        print("warning: using local hash embeddings — dev/test only, not for production retrieval")
    embedder = EmbeddingClient(
        provider=args.embed_provider,
        base_url=args.embed_url,
        api_key=args.embed_key,
        model=args.embed_model,
        dimension=args.dim,
    )
    store = MilvusKnowledgeStore(
        host=args.host,
        port=args.port,
        collection_name=args.collection,
        dimension=args.dim,
    )
    result = import_diakg(
        input_path=input_path,
        store=store,
        embedder=embedder,
        chunk_size=args.chunk_size,
        chunk_overlap=args.chunk_overlap,
        batch_size=args.batch_size,
    )
    print(
        f"import done: chunks={result['chunks_upserted']}, "
        f"entities={result['collection_entities']}, doc_type={result['doc_type']}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
