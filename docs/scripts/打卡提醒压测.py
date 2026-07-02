# -*- coding: utf-8 -*-
"""
打卡提醒 — 设置页 + 在线轮询压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

覆盖接口（与 CheckinReminderSettings 页面及 App 全局轮询一致）:
  设置页加载:  GET /user/profile + GET /checkin/reminders/rules
  恢复推荐:    GET /checkin/reminders/defaults
  保存设置:    PUT /checkin/reminders/rules
  在线轮询:    GET /checkin/reminders/pending（App 登录后每 60s）
  用户交互:    POST /checkin/reminders/logs/{logId}/ack（有待提醒时）

相对录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录 → 写入默认提醒规则
  - catch_response + validate_api_response + wr.checkpoint
  - 拆分「设置页事务」与「轮询事务」，可按混合比模拟真实流量
  - pending 有待办时自动 ack，避免 snooze 次数上限导致失败

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_SKIP_SAVE           true 时跳过 defaults + PUT（仅压设置页只读）
  WR_ONLY_POLL           true 时仅压 pending 轮询事务
  WR_POLL_WEIGHT         轮询事务混合比，默认 2（相对设置页 1）
  WR_PENDING_ACTION      ack | snooze | none，默认 ack
  WR_ROUND_SLEEP         每轮事务结束休眠秒数，默认 1
"""
import copy
import itertools
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
SKIP_SAVE = os.getenv("WR_SKIP_SAVE", "false").lower() == "true"
ONLY_POLL = os.getenv("WR_ONLY_POLL", "false").lower() == "true"
POLL_WEIGHT = max(1, int(os.getenv("WR_POLL_WEIGHT", "2")))
PENDING_ACTION = os.getenv("WR_PENDING_ACTION", "ack").lower()
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "1"))

REFERER_SETTINGS = "/checkin-reminder-settings"
REFERER_APP = "/"

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


def build_headers(referer_path: str = REFERER_SETTINGS) -> dict:
    return {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Content-Type": "application/json",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }


def build_auth_headers(token: str, referer_path: str = REFERER_SETTINGS) -> dict:
    headers = build_headers(referer_path)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_register_payload(user) -> dict:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"rtx_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"156{phone_suffix}"
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


def validate_defaults_response(res) -> bool:
    if not validate_api_response(res, "提醒默认规则", expect_data=True):
        return False

    data = res.json().get("data") or {}
    rules = data.get("rules") if isinstance(data, dict) else data
    ok = isinstance(rules, list) and len(rules) > 0
    if not ok:
        msg = "提醒默认规则 rules 为空"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint("提醒默认规则-rules非空", ok)
    except Exception:
        pass

    if hasattr(res, "success"):
        res.success()
    return True


def validate_pending_response(res) -> Tuple[bool, List[str]]:
    if not validate_api_response(res, "待提醒列表", expect_data=True):
        return False, []

    data = res.json().get("data")
    if not isinstance(data, list):
        msg = "待提醒列表 data 非数组"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False, []

    log_ids = []
    for item in data:
        if not isinstance(item, dict):
            continue
        log_id = item.get("logId") or item.get("log_id")
        if log_id:
            log_ids.append(str(log_id))

    try:
        wr.checkpoint("待提醒列表-结构正确", True)
    except Exception:
        pass

    if hasattr(res, "success"):
        res.success()
    return True, log_ids


def build_default_reminder_rules() -> dict:
    """与前端 mockDefaults / 录制脚本一致的 8 条默认规则。"""
    return {
        "rules": [
            {"checkinType": 1, "remindTime": "08:00", "enabled": True, "sortOrder": 0},
            {"checkinType": 1, "remindTime": "12:00", "enabled": True, "sortOrder": 1},
            {"checkinType": 1, "remindTime": "18:00", "enabled": True, "sortOrder": 2},
            {"checkinType": 2, "remindTime": "17:30", "enabled": True, "sortOrder": 0},
            {"checkinType": 3, "remindTime": "08:00", "enabled": True, "sortOrder": 0},
            {"checkinType": 3, "remindTime": "20:00", "enabled": True, "sortOrder": 1},
            {"checkinType": 4, "remindTime": "07:00", "enabled": True, "sortOrder": 0},
            {"checkinType": 4, "remindTime": "10:00", "enabled": True, "sortOrder": 1},
        ]
    }


def build_reminder_rules_variant(round_no: int = 1) -> dict:
    """保存时微调一条时段，避免完全重复写入。"""
    payload = copy.deepcopy(build_default_reminder_rules())
    minute = (round_no * 7) % 60
    payload["rules"][1]["remindTime"] = f"12:{minute:02d}"
    return payload


class _ReminderMixin:
    parent: "User"

    def _auth_headers(self, referer_path: str = REFERER_SETTINGS) -> Optional[dict]:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("提醒接口跳过: 未登录")
            return None
        return build_auth_headers(ctx["token"], referer_path)

    def _get_reminder(self, path: str, *, name: str, referer: str = REFERER_SETTINGS) -> bool:
        headers = self._auth_headers(referer)
        if not headers:
            return False

        url = f"{BASE_HOST}{path}"
        with self.get(url, headers=headers, name=name, catch_response=True) as res:
            return validate_api_response(res, name, expect_data=True)

    def _put_rules(self, payload: dict, *, name: str = "保存提醒规则") -> bool:
        headers = self._auth_headers(REFERER_SETTINGS)
        if not headers:
            return False

        url = f"{BASE_HOST}/api/v1/checkin/reminders/rules"
        with self.put(url, headers=headers, json=payload, name=name, catch_response=True) as res:
            return validate_api_response(res, name, expect_data=True)


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "打卡提醒压测"

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
    设置页读写 + 轮询混合场景。

    仅压轮询: WR_ONLY_POLL=true，用户数可 50+。
    含保存 PUT: 建议 30~50 用户；只读 WR_SKIP_SAVE=true 可略高。
    """

    场景名称 = "打卡提醒-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 50,
        "创建速率": 10,
        "运行时长": 300,
    }


class Transaction_Dakatixing(_ReminderMixin, SerialTransaction):
    """
    打卡提醒设置页完整流程（与浏览器录制一致）:
      profile → rules → defaults → PUT rules
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "dakatixing"

    def on_start(self):
        ctx = get_auth_ctx(self.parent)
        if ctx is not None:
            ctx["round"] = int(ctx.get("round", 0)) + 1
        super().on_start()

    @task
    def task_0(self):
        headers = self._auth_headers(REFERER_SETTINGS)
        if not headers:
            return

        url = f"{BASE_HOST}/api/v1/user/profile"
        with self.get(url, headers=headers, name="用户资料", catch_response=True) as res:
            validate_api_response(res, "用户资料", expect_data=True)

    @task
    def task_1(self):
        self._get_reminder("/api/v1/checkin/reminders/rules", name="查询提醒规则")

    @task
    def task_2(self):
        if SKIP_SAVE:
            return

        headers = self._auth_headers(REFERER_SETTINGS)
        if not headers:
            return

        url = f"{BASE_HOST}/api/v1/checkin/reminders/defaults"
        with self.get(url, headers=headers, name="提醒默认规则", catch_response=True) as res:
            validate_defaults_response(res)

    @task
    def task_3(self):
        if SKIP_SAVE:
            return

        ctx = get_auth_ctx(self.parent)
        round_no = int((ctx or {}).get("round", 1))
        self._put_rules(build_reminder_rules_variant(round_no))

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class Transaction_Dakatixinglunxun(_ReminderMixin, SerialTransaction):
    """
    App 全局轮询 + 可选用户交互:
      GET pending → POST ack/snooze（有待办 logId 时）
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)
        self._pending_log_ids: List[str] = []

    @property
    def transaction(self):
        return "dakatixing_lunxun"

    def on_start(self):
        self._pending_log_ids = []
        super().on_start()

    @task
    def task_0(self):
        headers = self._auth_headers(REFERER_APP)
        if not headers:
            return

        url = f"{BASE_HOST}/api/v1/checkin/reminders/pending"
        with self.get(url, headers=headers, name="待提醒列表", catch_response=True) as res:
            ok, log_ids = validate_pending_response(res)
            if ok:
                self._pending_log_ids = log_ids

    @task
    def task_1(self):
        if PENDING_ACTION == "none" or not self._pending_log_ids:
            return

        log_id = self._pending_log_ids[0]
        headers = self._auth_headers(REFERER_APP)
        if not headers:
            return

        if PENDING_ACTION == "snooze":
            url = f"{BASE_HOST}/api/v1/checkin/reminders/logs/{log_id}/snooze"
            payload = {"minutes": 15}
            with self.post(
                url,
                headers=headers,
                json=payload,
                name="稍后提醒",
                catch_response=True,
            ) as res:
                validate_api_response(res, "稍后提醒")
        else:
            url = f"{BASE_HOST}/api/v1/checkin/reminders/logs/{log_id}/ack"
            with self.post(
                url,
                headers=headers,
                json={},
                name="忽略提醒",
                catch_response=True,
            ) as res:
                validate_api_response(res, "忽略提醒")

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 设置页 + 在线轮询，混合比可配。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("dakatixing")
    def task_dakatixing(self):
        if ONLY_POLL:
            return
        Transaction_Dakatixing(self).run()

    @task(POLL_WEIGHT)
    @transaction("dakatixing_lunxun")
    def task_dakatixing_lunxun(self):
        Transaction_Dakatixinglunxun(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权并写入默认规则，再循环压测。"""

    host = BASE_HOST
    tasks = [WebrunnerAction]

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._auth_ctx = None

    def on_start(self):
        super().on_start()
        self._warmup_register()
        self._warmup_login()
        self._warmup_seed_rules()
        logger.info(
            f"打卡提醒虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"skip_save={SKIP_SAVE}, only_poll={ONLY_POLL}, poll_weight={POLL_WEIGHT}"
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

    def _warmup_seed_rules(self):
        """写入默认规则，使 pending 轮询在白天有可测数据。"""
        if not self._auth_ctx or not self._auth_ctx.get("token"):
            return

        url = f"{BASE_HOST}/api/v1/checkin/reminders/rules"
        headers = build_auth_headers(self._auth_ctx["token"], REFERER_SETTINGS)
        payload = build_default_reminder_rules()
        with self.client.put(
            url,
            headers=headers,
            json=payload,
            name="预热提醒规则",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "预热提醒规则", expect_data=True):
                logger.warning(
                    f"预热提醒规则失败: username={self._auth_ctx['username']}"
                )

# --- END OF SCRIPT ---
