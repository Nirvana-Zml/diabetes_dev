# -*- coding: utf-8 -*-
"""
打卡 AI 行为分析 — ai-summary 专项压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

相对浏览器录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录
  - 日期范围动态计算（默认近 7 天，与页面「近一周」一致）
  - ai-summary 客户端超时默认 180s（Dify 同步调用，避免 20s 默认超时）
  - 校验响应 summary / source（dify 或 local）
  - 低并发场景预设（Dify 瓶颈，勿用 50 用户）

事务 dakaxingweiAIfenxi（完整打开分析页）:
  task_0  GET /checkin-management/stats?startDate=&endDate=
  task_1  GET /checkin-management/trends?startDate=&endDate=
  task_2  GET /checkin-management/ai-summary?startDate=&endDate=  （核心，Dify）

仅压 ai-summary 时设置环境变量 WR_ONLY_AI_SUMMARY=true（跳过 stats/trends）。

环境变量（均可选）:
  WR_BASE_HOST              默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD       默认 Test123456
  WR_ANALYSIS_DAYS          统计区间天数-1，默认 6（共 7 天）
  WR_AI_SUMMARY_TIMEOUT     ai-summary 超时秒数，默认 180（须为 int）
  WR_NETWORK_TIMEOUT        客户端超时秒数，默认 max(190, timeout+10)
  WR_ONLY_AI_SUMMARY        true 时仅压 ai-summary
  WR_ROUND_SLEEP            每轮事务结束休眠秒数，默认 3
"""
import itertools
import os
import threading
import time
from datetime import date, timedelta
from typing import Dict, Optional, Tuple

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
ANALYSIS_DAYS = int(os.getenv("WR_ANALYSIS_DAYS", "6"))
AI_SUMMARY_TIMEOUT = int(os.getenv("WR_AI_SUMMARY_TIMEOUT", "180"))
NETWORK_TIMEOUT = int(os.getenv("WR_NETWORK_TIMEOUT", str(max(AI_SUMMARY_TIMEOUT + 10, 190))))
ONLY_AI_SUMMARY = os.getenv("WR_ONLY_AI_SUMMARY", "false").lower() == "true"
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "3"))

REFERER = "/checkin-analysis"

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


def build_date_range() -> Tuple[str, str]:
    end = date.today()
    start = end - timedelta(days=ANALYSIS_DAYS)
    return start.isoformat(), end.isoformat()


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
    username = f"ai_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"155{phone_suffix}"
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


def validate_ai_summary_response(res) -> bool:
    if not validate_api_response(res, "打卡AI分析", expect_data=True):
        return False

    data = res.json().get("data") or {}
    summary = data.get("summary") or data.get("ai_summary") or data.get("aiSummary")
    source = data.get("source")

    ok = bool(summary) or source in ("dify", "local")
    if not ok:
        msg = "打卡AI分析 缺少 summary 且 source 无效"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint("打卡AI分析-summary或source有效", ok)
        if source:
            wr.checkpoint("打卡AI分析-source存在", True)
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


class _CheckinAnalysisMixin:
    parent: "User"

    def _date_params(self) -> dict:
        start_date, end_date = build_date_range()
        return {"startDate": start_date, "endDate": end_date}

    def _get_mgmt(self, path: str, *, name: str) -> bool:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning(f"{name}跳过: 未登录")
            return False

        url = f"{BASE_HOST}{path}"
        headers = build_auth_headers(ctx["token"])
        with self.get(
            url,
            headers=headers,
            params=self._date_params(),
            name=name,
            catch_response=True,
        ) as res:
            return validate_api_response(res, name, expect_data=True)


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "打卡AI行为分析压测"

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
    Dify ai-summary 专项场景（低并发）。

    仅压 ai-summary: 用户数 5~8、创建速率 1。
    含 stats/trends 完整页: 可适当提高但勿超过 20 用户。
    """

    场景名称 = "打卡AI分析-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 8,
        "创建速率": 1,
        "运行时长": 600,
    }


class Transaction_Dakaxingweiaifenxi(_CheckinAnalysisMixin, SerialTransaction):
    """
    打卡 AI 行为分析:
      stats → trends → ai-summary（Dify）
    录制脚本仅含 ai-summary 时，设置 WR_ONLY_AI_SUMMARY=true。
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "dakaxingweiAIfenxi"

    def on_start(self):
        super().on_start()

    @task
    def task_0(self):
        if ONLY_AI_SUMMARY:
            return
        self._get_mgmt("/api/v1/checkin-management/stats", name="打卡分析统计")

    @task
    def task_1(self):
        if ONLY_AI_SUMMARY:
            return
        self._get_mgmt("/api/v1/checkin-management/trends", name="打卡分析趋势")

    @task
    def task_2(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("打卡AI分析跳过: 未登录")
            return

        start_date, end_date = build_date_range()
        url = f"{BASE_HOST}/api/v1/checkin-management/ai-summary"
        headers = build_auth_headers(ctx["token"])
        params = {"startDate": start_date, "endDate": end_date}
        started = time.time()
        try:
            with self.get(
                url,
                headers=headers,
                params=params,
                name="打卡AI分析",
                catch_response=True,
                timeout=AI_SUMMARY_TIMEOUT,
            ) as res:
                elapsed = time.time() - started
                logger.info(
                    f"打卡AI分析耗时: {elapsed:.2f}s "
                    f"(period={start_date}~{end_date}, timeout={AI_SUMMARY_TIMEOUT}s)"
                )
                validate_ai_summary_response(res)
        except Exception as exc:
            elapsed = time.time() - started
            msg = (
                f"打卡AI分析异常（elapsed={elapsed:.2f}s, "
                f"timeout={AI_SUMMARY_TIMEOUT}s, client={NETWORK_TIMEOUT}s）: {exc}"
            )
            logger.error(msg)
            try:
                wr.checkpoint("打卡AI分析-请求成功", False)
            except Exception:
                pass

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 打卡 AI 分析，混合比 100%。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("dakaxingweiAIfenxi")
    def task_dakaxingweiAIfenxi(self):
        Transaction_Dakaxingweiaifenxi(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环请求 ai-summary。"""

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
        start_date, end_date = build_date_range()
        logger.info(
            f"AI分析虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"period={start_date}~{end_date}, only_ai={ONLY_AI_SUMMARY}, "
            f"ai_timeout={AI_SUMMARY_TIMEOUT}s"
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
