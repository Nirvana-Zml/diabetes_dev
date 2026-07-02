# -*- coding: utf-8 -*-
"""
AI 科普助手 — 问答 SSE 专项压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

覆盖接口（与 /assistant 页面、AiChatDialog 发送一致）:
  POST /api/v1/chat/qa   Milvus 检索 + Dify 工作流 SSE 流式回答

相对浏览器录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录（可用 WR_GUEST_MODE 测游客）
  - 读取完整 SSE 流直至 message_end / error，避免连接过早关闭
  - 校验收到 text 内容与 conversationId
  - 问题池轮换（与页面快捷问题一致）
  - 可选多轮对话 WR_MULTI_TURN=true（携带 conversationId）
  - 低并发预设（Dify + Milvus 瓶颈，勿用 50 用户）

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_GUEST_MODE          true 时跳过登录，以游客身份问答
  WR_QA_TIMEOUT          单次问答超时秒数，默认 120（须为 int）
  WR_NETWORK_TIMEOUT     HTTP 客户端超时，默认 max(130, QA+10)
  WR_MULTI_TURN          true 时同一事务内连问 2 轮（测会话上下文）
  WR_ROUND_SLEEP         每轮事务结束休眠秒数，默认 3
"""
import itertools
import json
import os
import threading
import time
from typing import Dict, List, Optional, Tuple

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
GUEST_MODE = os.getenv("WR_GUEST_MODE", "false").lower() == "true"
QA_TIMEOUT = int(os.getenv("WR_QA_TIMEOUT", "120"))
NETWORK_TIMEOUT = int(os.getenv("WR_NETWORK_TIMEOUT", str(max(QA_TIMEOUT + 10, 130))))
MULTI_TURN = os.getenv("WR_MULTI_TURN", "false").lower() == "true"
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "3"))

REFERER = "/assistant"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/114.0.0.0 Safari/537.36"
)

QA_QUESTIONS = [
    "糖尿病可以吃水果吗？",
    "空腹血糖多少正常？",
    "如何预防糖尿病？",
    "运动后血糖低怎么办？",
    "二甲双胍有哪些常见副作用？",
    "糖尿病患者每天应该运动多久？",
]

FOLLOWUP_QUESTIONS = [
    "那具体有哪些水果比较适合？",
    "如果偏高应该怎么调整？",
    "还有没有其他注意事项？",
    "低的时候需要立刻吃糖吗？",
]

_seq_lock = threading.Lock()
_seq_counter = itertools.count(1)


def _next_seq() -> int:
    with _seq_lock:
        return next(_seq_counter) + int(time.time() * 1000) % 1_000_000


def resolve_root_user(node):
    while hasattr(node, "parent") and node.parent is not None:
        node = node.parent
    return node


def build_headers(referer_path: str = REFERER, *, sse: bool = False) -> dict:
    headers = {
        "Accept": "text/event-stream" if sse else "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Content-Type": "application/json",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }
    return headers


def build_auth_headers(token: str, referer_path: str = REFERER, *, sse: bool = False) -> dict:
    headers = build_headers(referer_path, sse=sse)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_register_payload(user) -> dict:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"qa_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"153{phone_suffix}"
    return {
        "username": username,
        "phone": phone,
        "password": DEFAULT_PASSWORD,
    }


def get_auth_ctx(node) -> Optional[Dict]:
    root = resolve_root_user(node)
    ctx = getattr(root, "_auth_ctx", None)
    if GUEST_MODE:
        return ctx or {}
    if not ctx or not ctx.get("token"):
        return None
    return ctx


def bump_round(ctx: Optional[Dict]) -> int:
    if ctx is None:
        return 1
    ctx["round"] = int(ctx.get("round", 0)) + 1
    return ctx["round"]


def pick_question(user, round_no: int, *, followup: bool = False) -> str:
    pool = FOLLOWUP_QUESTIONS if followup else QA_QUESTIONS
    user_id = getattr(user, "id", id(user)) if user else 0
    idx = (user_id + round_no) % len(pool)
    return pool[idx]


def validate_api_response(res, checkpoint_prefix: str, *, expect_data: bool = False) -> bool:
    if res.status_code != 200:
        msg = f"{checkpoint_prefix} HTTP 异常: {res.status_code}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        body = res.json()
    except Exception as exc:
        msg = f"{checkpoint_prefix} 响应非 JSON: {exc}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    biz_code = body.get("code")
    if biz_code != 200:
        msg = f"{checkpoint_prefix} 业务失败 code={biz_code}, message={body.get('message')}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    if expect_data and body.get("data") is None:
        msg = f"{checkpoint_prefix} 缺少 data 字段"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint(f"{checkpoint_prefix}-HTTP200", True)
        wr.checkpoint(f"{checkpoint_prefix}-业务成功", True)
    except Exception:
        pass

    if hasattr(res, "success"):
        res.success()
    return True


def validate_login_response(res) -> bool:
    if not validate_api_response(res, "登录"):
        return False

    token = (res.json().get("data") or {}).get("access_token")
    if not token:
        msg = "登录成功但 access_token 为空"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint("登录-token非空", bool(token))
    except Exception:
        pass
    return True


def _read_response_body(res) -> str:
    if callable(getattr(res, "iter_content", None)):
        chunks: List[bytes] = []
        for chunk in res.iter_content(chunk_size=4096):
            if chunk:
                chunks.append(chunk)
        return b"".join(chunks).decode("utf-8", errors="ignore")

    if hasattr(res, "text") and res.text:
        return res.text

    if hasattr(res, "content") and res.content:
        raw = res.content
        if isinstance(raw, bytes):
            return raw.decode("utf-8", errors="ignore")
        return str(raw)

    return ""


def _parse_sse_block(block: str) -> Tuple[str, str]:
    event_name = "message"
    data_str = ""
    for line in block.split("\n"):
        line = line.strip()
        if line.startswith("event:"):
            event_name = line[6:].strip()
        elif line.startswith("data:"):
            data_str = line[5:].strip()
    return event_name, data_str


def _parse_sse_payload(data_str: str) -> dict:
    if not data_str:
        return {}
    try:
        return json.loads(data_str)
    except Exception:
        return {"raw": data_str}


def validate_qa_sse_response(res, checkpoint_prefix: str) -> Tuple[bool, Optional[str]]:
    """
    解析科普问答 SSE，成功条件:
      - HTTP 200
      - 收到 message/text 内容或 message_end
      - 未收到 error 事件
    """
    if res.status_code != 200:
        msg = f"{checkpoint_prefix} HTTP 异常: {res.status_code}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False, None

    body = _read_response_body(res)
    got_text = False
    got_end = False
    got_error = False
    conv_id: Optional[str] = None
    text_len = 0

    for block in body.split("\n\n"):
        block = block.strip()
        if not block:
            continue

        event_name, data_str = _parse_sse_block(block)
        payload = _parse_sse_payload(data_str)

        if event_name == "error" or payload.get("type") == "error":
            got_error = True
            logger.warning(
                f"{checkpoint_prefix} SSE error: {payload.get('message') or data_str}"
            )
            continue

        cid = payload.get("conversationId") or payload.get("conversation_id")
        if cid:
            conv_id = str(cid)

        if event_name in ("message", "message_end"):
            if payload.get("type") == "text" and payload.get("content"):
                got_text = True
                text_len += len(str(payload.get("content")))
            if payload.get("type") == "end" or event_name == "message_end":
                got_end = True

    ok = (got_text or got_end) and not got_error
    if not ok:
        msg = (
            f"{checkpoint_prefix} SSE 无效: text={got_text}, end={got_end}, "
            f"error={got_error}, body_len={len(body)}"
        )
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False, conv_id

    try:
        wr.checkpoint(f"{checkpoint_prefix}-HTTP200", True)
        wr.checkpoint(f"{checkpoint_prefix}-SSE有效", ok)
        if conv_id:
            wr.checkpoint(f"{checkpoint_prefix}-conversationId存在", True)
    except Exception:
        pass

    if hasattr(res, "success"):
        res.success()

    logger.info(
        f"{checkpoint_prefix} 完成: text_len={text_len}, end={got_end}, conv={conv_id}"
    )
    return True, conv_id


class _QaChatMixin:
    parent: "User"

    def _qa_headers(self) -> dict:
        ctx = get_auth_ctx(self.parent)
        token = (ctx or {}).get("token")
        if token:
            return build_auth_headers(token, REFERER, sse=True)
        return build_headers(REFERER, sse=True)

    def _post_qa(self, question: str, *, name: str, conversation_id: Optional[str] = None) -> Tuple[bool, Optional[str]]:
        ctx = get_auth_ctx(self.parent)
        if ctx is None:
            logger.warning(f"{name}跳过: 未登录")
            return False, None

        payload = {"query": question}
        if conversation_id:
            payload["conversationId"] = conversation_id

        url = f"{BASE_HOST}/api/v1/chat/qa"
        headers = self._qa_headers()
        started = time.time()

        try:
            with self.post(
                url,
                headers=headers,
                json=payload,
                name=name,
                catch_response=True,
                timeout=QA_TIMEOUT,
                stream=True,
            ) as res:
                elapsed = time.time() - started
                logger.info(f"{name} 耗时: {elapsed:.2f}s (timeout={QA_TIMEOUT}s)")
                return validate_qa_sse_response(res, name)
        except Exception as exc:
            elapsed = time.time() - started
            msg = (
                f"{name} 异常（elapsed={elapsed:.2f}s, "
                f"timeout={QA_TIMEOUT}s, client={NETWORK_TIMEOUT}s）: {exc}"
            )
            logger.error(msg)
            try:
                wr.checkpoint(f"{name}-请求成功", False)
            except Exception:
                pass
            return False, None


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "AI科普助手压测"

    测试机 = [
        {
            "ip地址": "192.168.22.28",
            "端口": 50000,
            "节点数": 1,
            "主节点": True,
        },
    ]


class 测试场景(WebScenario):
    """
    Dify 科普问答专项场景（低并发）。

    建议: 用户数 5~8、创建速率 1、运行时长 600s。
    """

    场景名称 = "AI科普助手-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 8,
        "创建速率": 1,
        "运行时长": 600,
    }


class Transaction_Kepuaiwenda(_QaChatMixin, SerialTransaction):
    """
    AI 科普问答（单 @task，避免空 task 导致 WebRunner 事务失败）:
      POST /chat/qa（SSE）
      WR_MULTI_TURN=true 时同 task 内追问一轮
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "kepuAIwenda"

    def on_start(self):
        root = resolve_root_user(self.parent)
        ctx = getattr(root, "_auth_ctx", None)
        round_no = bump_round(ctx)
        self._round_no = round_no
        super().on_start()

    @task
    def task_0(self):
        root = resolve_root_user(self.parent)
        question = pick_question(root, self._round_no, followup=False)
        ok, conv_id = self._post_qa(question, name="科普问答")
        ctx = get_auth_ctx(self.parent)
        if ok and conv_id and ctx is not None:
            ctx["conversation_id"] = conv_id

        if not MULTI_TURN or not ok or not conv_id:
            return

        followup = pick_question(root, self._round_no, followup=True)
        self._post_qa(
            followup,
            name="科普问答-追问",
            conversation_id=conv_id,
        )

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: AI 科普问答。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("kepuAIwenda")
    def task_kepuaiwenda(self):
        Transaction_Kepuaiwenda(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环发起科普问答。"""

    host = BASE_HOST
    tasks = [WebrunnerAction]
    network_timeout = NETWORK_TIMEOUT
    connection_timeout = NETWORK_TIMEOUT

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._auth_ctx = {"round": 0}

    def on_start(self):
        super().on_start()
        self._apply_client_timeout()
        if not GUEST_MODE:
            self._warmup_register()
            self._warmup_login()
        logger.info(
            f"科普问答虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"guest={GUEST_MODE}, multi_turn={MULTI_TURN}, qa_timeout={QA_TIMEOUT}s"
        )

    def _apply_client_timeout(self):
        for attr in ("network_timeout", "connection_timeout"):
            if hasattr(self.client, attr):
                setattr(self.client, attr, NETWORK_TIMEOUT)

    def _warmup_register(self):
        data = build_register_payload(self)
        url = f"{BASE_HOST}/api/v1/auth/register"
        headers = build_headers("/register")
        with self.client.post(
            url,
            headers=headers,
            json=data,
            name="预热注册",
            catch_response=True,
        ) as res:
            if validate_api_response(res, "预热注册"):
                self._auth_ctx.update(
                    {
                        "username": data["username"],
                        "phone": data["phone"],
                        "password": data["password"],
                        "token": None,
                    }
                )
            else:
                logger.error(f"预热注册失败: username={data['username']}")

    def _warmup_login(self):
        if not self._auth_ctx.get("username"):
            return

        data = {
            "username": self._auth_ctx["username"],
            "password": self._auth_ctx["password"],
        }
        url = f"{BASE_HOST}/api/v1/auth/login"
        headers = build_headers("/login")
        with self.client.post(
            url,
            headers=headers,
            json=data,
            name="预热登录",
            catch_response=True,
        ) as res:
            if validate_login_response(res):
                self._auth_ctx["token"] = res.json()["data"]["access_token"]
            else:
                logger.error(f"预热登录失败: username={self._auth_ctx['username']}")

# --- END OF SCRIPT ---
