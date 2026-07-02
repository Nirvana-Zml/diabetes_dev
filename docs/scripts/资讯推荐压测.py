# -*- coding: utf-8 -*-
"""
健康资讯 / 资讯推荐 — 压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

覆盖接口（与 /health-info 列表页录制一致）:
  GET  /api/v1/articles/favorites?page=1&size=200   预加载收藏 ID
  GET  /api/v1/articles?page=1&size=10              分类浏览
  GET  /api/v1/articles/recommend?page=1&size=10    为您推荐

可选详情路径 WR_ENABLE_DETAIL=true:
  GET  /api/v1/articles/{id}
  POST /api/v1/articles/{id}/read-event
  GET  /api/v1/articles/{id}/related?size=4
  POST /api/v1/articles/{id}/favorite

Dify 个性化推荐（Phase4 重排）请使用配套脚本:
  docs/scripts/资讯推荐Dify压测.py  （低并发，勿与 popular 列表页混压）

相对录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录
  - catch_response + validate_api_response + wr.checkpoint
  - 单 @task 列表流 + 可选详情，避免空 task 导致 WebRunner 事务失败
  - articleId 从列表响应动态提取；无文章时跳过详情（避免 HTTP 404 事务失败）
  - 默认 recommend 走 strategy=popular 快速路径（高并发安全）

环境变量（均可选）:
  WR_BASE_HOST              默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD       默认 Test123456
  WR_FAVORITES_SIZE         收藏预加载 size，默认 200
  WR_LIST_PAGE / WR_LIST_SIZE  列表分页，默认 1 / 10
  WR_RECOMMEND_STRATEGY     popular | skip，默认 popular（personalized 见 Dify 专项脚本）
  WR_ENABLE_DETAIL          true 时追加详情 + related + read-event + favorite
  WR_ROUND_SLEEP            每轮事务结束休眠秒数，默认 1
"""
import itertools
import os
import threading
import time
from typing import Dict, List, Optional

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
FAVORITES_SIZE = int(os.getenv("WR_FAVORITES_SIZE", "200"))
LIST_PAGE = int(os.getenv("WR_LIST_PAGE", "1"))
LIST_SIZE = int(os.getenv("WR_LIST_SIZE", "10"))
RECOMMEND_STRATEGY = os.getenv("WR_RECOMMEND_STRATEGY", "popular").lower()
if RECOMMEND_STRATEGY == "personalized":
    logger.warning(
        "WR_RECOMMEND_STRATEGY=personalized 请改用 docs/scripts/资讯推荐Dify压测.py；"
        "本脚本将按 popular 执行"
    )
    RECOMMEND_STRATEGY = "popular"
ENABLE_DETAIL = os.getenv("WR_ENABLE_DETAIL", "false").lower() == "true"
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "1"))

REFERER_LIST = "/health-info"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/114.0.0.0 Safari/537.36"
)

_seq_lock = threading.Lock()
_seq_counter = itertools.count(1)


def _next_seq() -> int:
    with _seq_lock:
        return next(_seq_counter) + int(time.time() * 1000) % 1_000_000


def resolve_root_user(node):
    while hasattr(node, "parent") and node.parent is not None:
        node = node.parent
    return node


def build_headers(referer_path: str = REFERER_LIST) -> dict:
    return {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Content-Type": "application/json",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }


def build_auth_headers(token: str, referer_path: str = REFERER_LIST) -> dict:
    headers = build_headers(referer_path)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_register_payload(user) -> dict:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"art_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"152{phone_suffix}"
    return {
        "username": username,
        "phone": phone,
        "password": DEFAULT_PASSWORD,
    }


def get_auth_ctx(node) -> Optional[Dict]:
    root = resolve_root_user(node)
    ctx = getattr(root, "_auth_ctx", None)
    if not ctx or not ctx.get("token"):
        return None
    return ctx


def bump_round(ctx: Optional[Dict]) -> int:
    if ctx is None:
        return 1
    ctx["round"] = int(ctx.get("round", 0)) + 1
    return ctx["round"]


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


def extract_article_id(item: dict) -> Optional[str]:
    if not isinstance(item, dict):
        return None
    article_id = (
        item.get("articleId")
        or item.get("article_id")
        or item.get("id")
    )
    return str(article_id) if article_id else None


def parse_article_list(res) -> List[str]:
    try:
        data = res.json().get("data") or {}
    except Exception:
        return []

    items = data.get("articles") or data.get("list") or data.get("items") or []
    if not isinstance(items, list):
        return []

    ids: List[str] = []
    for item in items:
        article_id = extract_article_id(item)
        if article_id:
            ids.append(article_id)
    return ids


def remember_article_id(ctx: Optional[Dict], article_id: Optional[str]) -> None:
    if ctx and article_id:
        ctx["last_article_id"] = article_id


class _ArticleListMixin:
    parent: "User"

    def _auth_headers(self, referer_path: str = REFERER_LIST) -> Optional[dict]:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("资讯接口跳过: 未登录")
            return None
        return build_auth_headers(ctx["token"], referer_path)

    def _post_article(
        self,
        path: str,
        *,
        name: str,
        payload: dict,
        referer: str,
    ) -> bool:
        headers = self._auth_headers(referer)
        if not headers:
            return False

        url = f"{BASE_HOST}{path}"
        with self.post(
            url,
            headers=headers,
            json=payload,
            name=name,
            catch_response=True,
        ) as res:
            return validate_api_response(res, name, expect_data=True)

    def _load_favorites(self, ctx: Optional[Dict]) -> List[str]:
        headers = self._auth_headers(REFERER_LIST)
        if not headers:
            return []

        url = f"{BASE_HOST}/api/v1/articles/favorites"
        ids: List[str] = []
        with self.get(
            url,
            headers=headers,
            params={"page": LIST_PAGE, "size": FAVORITES_SIZE},
            name="资讯收藏列表",
            catch_response=True,
        ) as res:
            if validate_api_response(res, "资讯收藏列表", expect_data=True):
                ids = parse_article_list(res)
                if ids:
                    remember_article_id(ctx, ids[0])
        return ids

    def _load_articles(self, ctx: Optional[Dict]) -> List[str]:
        headers = self._auth_headers(REFERER_LIST)
        if not headers:
            return []

        url = f"{BASE_HOST}/api/v1/articles"
        ids: List[str] = []
        with self.get(
            url,
            headers=headers,
            params={"page": LIST_PAGE, "size": LIST_SIZE},
            name="资讯文章列表",
            catch_response=True,
        ) as res:
            if validate_api_response(res, "资讯文章列表", expect_data=True):
                ids = parse_article_list(res)
                if ids:
                    remember_article_id(ctx, ids[0])
        return ids

    def _load_recommend(
        self,
        ctx: Optional[Dict],
        *,
        strategy: Optional[str],
        name: str = "资讯推荐",
    ) -> List[str]:
        if strategy == "skip":
            return []

        headers = self._auth_headers(REFERER_LIST)
        if not headers:
            return []

        params = {"page": LIST_PAGE, "size": LIST_SIZE}
        if strategy == "popular":
            params["strategy"] = "popular"

        timeout = None
        url = f"{BASE_HOST}/api/v1/articles/recommend"
        ids: List[str] = []
        kwargs = {
            "headers": headers,
            "params": params,
            "name": name,
            "catch_response": True,
        }
        if timeout is not None:
            kwargs["timeout"] = timeout

        started = time.time()
        with self.get(url, **kwargs) as res:
            elapsed = time.time() - started
            logger.info(f"{name} 耗时: {elapsed:.2f}s")
            if validate_api_response(res, name, expect_data=True):
                ids = parse_article_list(res)
                if ids:
                    remember_article_id(ctx, ids[0])
        return ids

    def _run_detail_flow(self, article_id: str) -> None:
        referer = f"/health-info/{article_id}"
        headers = self._auth_headers(referer)
        if not headers:
            return

        detail_url = f"{BASE_HOST}/api/v1/articles/{article_id}"
        with self.get(
            detail_url,
            headers=headers,
            name="资讯详情",
            catch_response=True,
        ) as res:
            if res.status_code == 404:
                logger.warning(f"资讯详情 404，跳过: {article_id}")
                return
            if not validate_api_response(res, "资讯详情", expect_data=True):
                return

        self._post_article(
            f"/api/v1/articles/{article_id}/read-event",
            name="资讯阅读上报",
            payload={"duration_sec": 0, "source": "detail"},
            referer=referer,
        )

        related_url = f"{BASE_HOST}/api/v1/articles/{article_id}/related"
        with self.get(
            related_url,
            headers=headers,
            params={"size": 4},
            name="相关资讯",
            catch_response=True,
        ) as res:
            validate_api_response(res, "相关资讯", expect_data=True)

        self._post_article(
            f"/api/v1/articles/{article_id}/favorite",
            name="资讯收藏",
            payload={},
            referer=referer,
        )

        self._post_article(
            f"/api/v1/articles/{article_id}/read-event",
            name="资讯阅读离开",
            payload={"duration_sec": 30, "source": "detail"},
            referer=referer,
        )


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "资讯推荐压测"

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
    列表页 popular 推荐，可高并发。
    Dify 个性化请使用 docs/scripts/资讯推荐Dify压测.py。
    """

    场景名称 = "资讯推荐-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 50,
        "创建速率": 10,
        "运行时长": 300,
    }


class Transaction_Zixuntuijian(_ArticleListMixin, SerialTransaction):
    """
    资讯列表页（与录制一致）:
      favorites(200) → articles → recommend
      可选详情路径 WR_ENABLE_DETAIL=true
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "zixuntuijian"

    def on_start(self):
        root = resolve_root_user(self.parent)
        ctx = getattr(root, "_auth_ctx", None)
        self._round_no = bump_round(ctx)
        super().on_start()

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        all_ids: List[str] = []

        fav_ids = self._load_favorites(ctx)
        all_ids.extend(fav_ids)

        art_ids = self._load_articles(ctx)
        all_ids.extend(art_ids)

        rec_ids = self._load_recommend(
            ctx,
            strategy=RECOMMEND_STRATEGY,
            name="资讯推荐",
        )
        all_ids.extend(rec_ids)

        if not ENABLE_DETAIL:
            return

        article_id = (ctx or {}).get("last_article_id")
        if not article_id and all_ids:
            article_id = all_ids[0]
        if not article_id:
            logger.info("资讯详情跳过: 列表无 articleId")
            return

        self._run_detail_flow(article_id)

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 资讯列表页（popular 快速路径）。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("zixuntuijian")
    def task_zixuntuijian(self):
        Transaction_Zixuntuijian(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环加载资讯推荐页。"""

    host = BASE_HOST
    tasks = [WebrunnerAction]

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._auth_ctx = None

    def on_start(self):
        super().on_start()
        self._warmup_register()
        self._warmup_login()
        logger.info(
            f"资讯推荐虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"recommend_strategy={RECOMMEND_STRATEGY}, enable_detail={ENABLE_DETAIL}"
        )

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
                self._auth_ctx = {
                    "username": data["username"],
                    "phone": data["phone"],
                    "password": data["password"],
                    "token": None,
                    "round": 0,
                    "last_article_id": None,
                }
            else:
                logger.error(f"预热注册失败: username={data['username']}")

    def _warmup_login(self):
        if not self._auth_ctx:
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
