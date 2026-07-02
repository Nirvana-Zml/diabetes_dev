# -*- coding: utf-8 -*-
"""
健康方案 — 页面加载（只读）压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

场景 A 覆盖接口（与 LivingPlans 页面 onMounted 一致）:
  GET /api/v1/plan/history?page=1&size=10
  GET /api/v1/plan/latest

可选扩展（WR_ENABLE_DETAIL=true）:
  GET /api/v1/plan/{planId}  — 点击历史方案条目

相对录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录
  - catch_response + validate_api_response + wr.checkpoint
  - latest 无方案时 HTTP 404 显式 res.success()（避免事务失败）
  - 仅 2 个 @task（空 task 会导致 WebRunner 事务失败）
  - history 空列表（total=0）视为正常

Dify 生成方案 POST /api/v1/plan/generate 为 SSE 长连接，勿与本脚本混压；
  请单独低并发压测（建议 5~8 用户，超时 180s）。

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_PLAN_PAGE           历史分页 page，默认 1
  WR_PLAN_SIZE           历史分页 size，默认 10
  WR_ENABLE_DETAIL       true 时追加 GET /plan/{id}（history/latest 有 planId 时）
  WR_FORCE_LATEST        true 时无历史也仍请求 latest（404 会导致 WebRunner 事务失败）
  WR_ROUND_SLEEP         每轮事务结束休眠秒数，默认 1

WebRunner 事务判定说明:
  SerialTransaction 在 crequestdebug 中仅将 200/201/…/308 视为成功，
  404 会使整笔事务失败（与 Locust catch_response 无关）。
  新用户无方案时请勿请求 /plan/latest，除非 WR_FORCE_LATEST=true。
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
PLAN_PAGE = int(os.getenv("WR_PLAN_PAGE", "1"))
PLAN_SIZE = int(os.getenv("WR_PLAN_SIZE", "10"))
ENABLE_DETAIL = os.getenv("WR_ENABLE_DETAIL", "false").lower() == "true"
FORCE_LATEST = os.getenv("WR_FORCE_LATEST", "false").lower() == "true"
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "1"))

REFERER = "/living-plans"

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
    username = f"pln_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"159{phone_suffix}"
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
        wr.checkpoint(f"{checkpoint_prefix}-HTTP200", res.status_code == 200)
        wr.checkpoint(f"{checkpoint_prefix}-业务成功", biz_code == 200)
    except Exception:
        pass

    if hasattr(res, "success"):
        res.success()
    return True


def validate_http_ok(res, checkpoint_prefix: str, *, accept_status=(200,)) -> bool:
    if res.status_code not in accept_status:
        msg = f"{checkpoint_prefix} HTTP 异常: {res.status_code}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint(f"{checkpoint_prefix}-HTTP{res.status_code}", True)
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


def extract_plan_id(item: dict) -> Optional[str]:
    if not isinstance(item, dict):
        return None
    plan_id = item.get("planId") or item.get("plan_id")
    return str(plan_id) if plan_id else None


def parse_history_meta(res) -> tuple[int, Optional[str]]:
    """history 已通过校验后，返回 (total, 首个 planId)。"""
    try:
        data = res.json().get("data") or {}
    except Exception:
        return 0, None

    plans = data.get("plans") or data.get("list") or []
    total = data.get("total")
    if total is None and isinstance(plans, list):
        total = len(plans)
    try:
        total = int(total or 0)
    except (TypeError, ValueError):
        total = 0

    if not isinstance(plans, list):
        return total, None

    for item in plans:
        plan_id = extract_plan_id(item)
        if plan_id:
            return total, plan_id
    return total, None


def validate_latest_response(res) -> Optional[str]:
    """校验 latest（调用方应保证仅在 history.total>0 或 WR_FORCE_LATEST 时请求）。"""
    if res.status_code == 404:
        # WebRunner 事务层仍会将 404 记为失败；此处仅做日志与 catch_response 标记。
        validate_http_ok(res, "最新方案-无记录", accept_status=(404,))
        logger.warning(
            "latest 返回 404：WebRunner 事务仍会失败。"
            "新用户请关闭 WR_FORCE_LATEST，或在 history 有数据后再请求 latest。"
        )
        return None

    if res.status_code == 200:
        if not validate_api_response(res, "最新方案", expect_data=True):
            return None
        data = res.json().get("data") or {}
        return extract_plan_id(data)

    msg = f"最新方案 HTTP 异常: {res.status_code}"
    logger.warning(msg)
    if hasattr(res, "failure"):
        res.failure(msg)
    return None


def should_fetch_latest(ctx: Optional[Dict]) -> bool:
    if FORCE_LATEST:
        return True
    total = int((ctx or {}).get("history_total") or 0)
    return total > 0


def remember_plan_id(ctx: Optional[Dict], plan_id: Optional[str]) -> None:
    if ctx and plan_id:
        ctx["last_plan_id"] = plan_id


class _PlanPageMixin:
    parent: "User"

    def _auth_headers(self) -> Optional[dict]:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("健康方案接口跳过: 未登录")
            return None
        return build_auth_headers(ctx["token"], REFERER)


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "健康方案页面加载压测"

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
    只读页面加载，可承受较高并发。
    默认 50 用户 / 10 创建速率 / 300s。
    """

    场景名称 = "健康方案-页面加载-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 50,
        "创建速率": 10,
        "运行时长": 300,
    }


class Transaction_Jiankangfanganshengcheng(_PlanPageMixin, SerialTransaction):
    """
    健康方案页面加载:
      history → latest（仅 history.total>0 时，避免 WebRunner 对 404 判事务失败）

    WebRunner 事务成功只看 HTTP 状态码白名单（200/201/…），404 必失败。
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "jiankangfanganshengcheng"

    def on_start(self):
        super().on_start()

    @task
    def task_0(self):
        headers = self._auth_headers()
        ctx = get_auth_ctx(self.parent)
        if not headers:
            logger.warning("方案历史跳过: 未登录")
            return

        url = f"{BASE_HOST}/api/v1/plan/history"
        params = {"page": PLAN_PAGE, "size": PLAN_SIZE}
        with self.get(
            url,
            headers=headers,
            params=params,
            name="方案历史",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "方案历史", expect_data=True):
                return
            total, plan_id = parse_history_meta(res)
            if ctx is not None:
                ctx["history_total"] = total
            remember_plan_id(ctx, plan_id)

        if not should_fetch_latest(ctx):
            logger.info(
                "最新方案跳过: history.total=0（WebRunner 对 HTTP 404 会判事务失败）"
            )
            return

        latest_url = f"{BASE_HOST}/api/v1/plan/latest"
        with self.get(
            latest_url,
            headers=headers,
            name="最新方案",
            catch_response=True,
        ) as res:
            plan_id = validate_latest_response(res)
            remember_plan_id(ctx, plan_id)

        if not ENABLE_DETAIL:
            return

        plan_id = (ctx or {}).get("last_plan_id")
        if not plan_id:
            return

        detail_url = f"{BASE_HOST}/api/v1/plan/{plan_id}"
        with self.get(
            detail_url,
            headers=headers,
            name="方案详情",
            catch_response=True,
        ) as res:
            if res.status_code == 404:
                validate_http_ok(res, "方案详情-不存在", accept_status=(404,))
            else:
                validate_api_response(res, "方案详情", expect_data=True)

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 健康方案页面加载。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("jiankangfanganshengcheng")
    def task_jiankangfanganshengcheng(self):
        Transaction_Jiankangfanganshengcheng(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环加载健康方案页。"""

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
            f"健康方案虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"page={PLAN_PAGE}, size={PLAN_SIZE}, enable_detail={ENABLE_DETAIL}, "
            f"force_latest={FORCE_LATEST}"
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
                    "last_plan_id": None,
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
