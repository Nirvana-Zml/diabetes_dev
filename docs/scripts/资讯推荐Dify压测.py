# -*- coding: utf-8 -*-
"""
资讯推荐 — Dify 个性化推荐专项压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

用途:
  专门压测 GET /api/v1/articles/recommend（登录用户、无 strategy=popular），
  触发 article-service Phase4 Dify 重排；与「资讯推荐压测.py」（popular 快速路径）互补。

事务 zixuntuijian_dify:
  GET /api/v1/articles/favorites?page=1&size=200   模拟列表页预加载（可 WR_SKIP_FAVORITES 跳过）
  GET /api/v1/articles/recommend?page=1&size=10    个性化推荐（Dify，核心）

相对列表页脚本的差异:
  - 不传 strategy=popular，走 personalized 全链路
  - 校验 strategy=personalized、articles 非空；可选校验 phase / rec_reason
  - 低并发场景预设（Dify 瓶颈，勿 50 用户）
  - recommend 超时默认 90s，同步提高客户端 network_timeout

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_FAVORITES_SIZE      收藏预加载 size，默认 200
  WR_LIST_PAGE / WR_LIST_SIZE  推荐分页，默认 1 / 10
  WR_SKIP_FAVORITES      true 时仅压 recommend（跳过 favorites）
  WR_RECOMMEND_TIMEOUT   recommend 超时秒数，默认 90（须为 int）
  WR_NETWORK_TIMEOUT     客户端超时，默认 max(100, timeout+10)
  WR_STRICT_DIFY         true 时要求 phase=4 或首条含 rec_reason
  WR_ROUND_SLEEP         每轮事务结束休眠秒数，默认 3
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
SKIP_FAVORITES = os.getenv("WR_SKIP_FAVORITES", "false").lower() == "true"
RECOMMEND_TIMEOUT = int(os.getenv("WR_RECOMMEND_TIMEOUT", "90"))
NETWORK_TIMEOUT = int(
    os.getenv("WR_NETWORK_TIMEOUT", str(max(RECOMMEND_TIMEOUT + 10, 100)))
)
STRICT_DIFY = os.getenv("WR_STRICT_DIFY", "false").lower() == "true"
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "3"))

REFERER = "/health-info"

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


def build_headers(referer_path: str = REFERER) -> dict:
    return {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Content-Type": "application/json",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }


def build_auth_headers(token: str, referer_path: str = REFERER) -> dict:
    headers = build_headers(referer_path)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_register_payload(user) -> dict:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"artd_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"151{phone_suffix}"
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


def validate_dify_recommend_response(res) -> bool:
    """校验个性化推荐响应（strategy=personalized，articles 非空）。"""
    if not validate_api_response(res, "资讯推荐-Dify", expect_data=True):
        return False

    data = res.json().get("data") or {}
    strategy = data.get("strategy")
    phase = data.get("phase")
    articles = data.get("articles") or data.get("list") or []

    if strategy != "personalized":
        msg = f"资讯推荐-Dify strategy 非 personalized: {strategy}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    if not isinstance(articles, list) or len(articles) == 0:
        msg = "资讯推荐-Dify articles 为空"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    first = articles[0] if isinstance(articles[0], dict) else {}
    rec_reason = first.get("recReason") or first.get("rec_reason")

    if STRICT_DIFY:
        phase_ok = phase == 4
        reason_ok = bool(rec_reason)
        if not (phase_ok or reason_ok):
            msg = f"资讯推荐-Dify 严格模式未命中 Dify 特征: phase={phase}, rec_reason={rec_reason}"
            logger.warning(msg)
            if hasattr(res, "failure"):
                res.failure(msg)
            return False

    try:
        wr.checkpoint("资讯推荐-Dify-strategy个性化", strategy == "personalized")
        wr.checkpoint("资讯推荐-Dify-articles非空", len(articles) > 0)
        if phase is not None:
            wr.checkpoint("资讯推荐-Dify-phase存在", True)
        if rec_reason:
            wr.checkpoint("资讯推荐-Dify-rec_reason存在", True)
    except Exception:
        pass

    if hasattr(res, "success"):
        res.success()

    logger.info(
        f"资讯推荐-Dify 成功: phase={phase}, count={len(articles)}, "
        f"rec_reason={'有' if rec_reason else '无'}"
    )
    return True


class _DifyRecommendMixin:
    parent: "User"

    def _auth_headers(self) -> Optional[dict]:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("资讯推荐-Dify 跳过: 未登录")
            return None
        return build_auth_headers(ctx["token"], REFERER)

    def _load_favorites(self) -> bool:
        headers = self._auth_headers()
        if not headers:
            return False

        url = f"{BASE_HOST}/api/v1/articles/favorites"
        with self.get(
            url,
            headers=headers,
            params={"page": LIST_PAGE, "size": FAVORITES_SIZE},
            name="资讯收藏列表",
            catch_response=True,
        ) as res:
            return validate_api_response(res, "资讯收藏列表", expect_data=True)

    def _load_personalized_recommend(self) -> bool:
        headers = self._auth_headers()
        if not headers:
            return False

        url = f"{BASE_HOST}/api/v1/articles/recommend"
        params = {"page": LIST_PAGE, "size": LIST_SIZE}
        started = time.time()

        try:
            with self.get(
                url,
                headers=headers,
                params=params,
                name="资讯推荐-Dify",
                catch_response=True,
                timeout=RECOMMEND_TIMEOUT,
            ) as res:
                elapsed = time.time() - started
                logger.info(
                    f"资讯推荐-Dify 耗时: {elapsed:.2f}s (timeout={RECOMMEND_TIMEOUT}s)"
                )
                return validate_dify_recommend_response(res)
        except Exception as exc:
            elapsed = time.time() - started
            msg = (
                f"资讯推荐-Dify 异常（elapsed={elapsed:.2f}s, "
                f"timeout={RECOMMEND_TIMEOUT}s, client={NETWORK_TIMEOUT}s）: {exc}"
            )
            logger.error(msg)
            try:
                wr.checkpoint("资讯推荐-Dify-请求成功", False)
            except Exception:
                pass
            return False


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "资讯推荐Dify压测"

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
    Dify 个性化推荐专项（低并发）。

    建议: 用户数 5~8、创建速率 1、运行时长 600s。
    勿用 50 用户 — Dify 为瓶颈资源。
    """

    场景名称 = "资讯推荐-Dify-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 8,
        "创建速率": 1,
        "运行时长": 600,
    }


class Transaction_ZixuntuijianDify(_DifyRecommendMixin, SerialTransaction):
    """
    Dify 个性化推荐（单 @task）:
      favorites（可选）→ recommend（无 strategy=popular）
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "zixuntuijian_dify"

    def on_start(self):
        super().on_start()

    @task
    def task_0(self):
        if not SKIP_FAVORITES:
            self._load_favorites()
        self._load_personalized_recommend()

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: Dify 个性化资讯推荐。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("zixuntuijian_dify")
    def task_zixuntuijian_dify(self):
        Transaction_ZixuntuijianDify(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环请求个性化 recommend。"""

    host = BASE_HOST
    tasks = [WebrunnerAction]
    network_timeout = NETWORK_TIMEOUT
    connection_timeout = NETWORK_TIMEOUT

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._auth_ctx = None

    def on_start(self):
        super().on_start()
        self._apply_client_timeout()
        self._warmup_register()
        self._warmup_login()
        logger.info(
            f"资讯Dify虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"skip_favorites={SKIP_FAVORITES}, strict_dify={STRICT_DIFY}, "
            f"recommend_timeout={RECOMMEND_TIMEOUT}s, client={NETWORK_TIMEOUT}s"
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
                self._auth_ctx = {
                    "username": data["username"],
                    "phone": data["phone"],
                    "password": data["password"],
                    "token": None,
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
