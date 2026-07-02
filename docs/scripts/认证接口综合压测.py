# -*- coding: utf-8 -*-
"""
认证接口综合压测脚本（WebRunner）

覆盖接口:
  - POST /api/v1/auth/register   用户注册
  - POST /api/v1/auth/login      用户登录
  - POST /api/v1/auth/send-code  发送验证码（忘记密码前置步骤）
  - POST /api/v1/auth/reset-password  重置密码

校验规则（与后端 DTO 一致）:
  - username: 3-50 字符，字母/数字/下划线/中文/连字符
  - phone:    11 位，1[3-9] 开头
  - password: 6-32 字符
  - login 成功响应 data.access_token 非空

忘记密码说明:
  与前端 ForgotPassword 一致，必须使用邮箱（type=email, purpose=reset_password）。
  虚拟用户预热时会注册、登录并尝试绑定唯一邮箱。
  验证码由服务端生成并通过邮件下发，接口不会返回验证码。
  压测环境下默认仅压测 send-code；若需完整 reset-password 流程，请将
  WR_ENABLE_RESET_PASSWORD 设为 true，并配置 WR_DEV_VERIFY_CODE（须与
  绑定/重置时邮件中的验证码一致，或配合测试环境固定验证码策略）。
"""
import itertools
import os
import threading
import time

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 可调整的全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
NEW_PASSWORD = os.getenv("WR_NEW_PASSWORD", "Test654321")

# 忘记密码：默认只压 send-code；完整 reset 需可获取验证码
ENABLE_RESET_PASSWORD = os.getenv("WR_ENABLE_RESET_PASSWORD", "false").lower() == "true"
DEV_VERIFY_CODE = os.getenv("WR_DEV_VERIFY_CODE", "")
EMAIL_DOMAIN = os.getenv("WR_EMAIL_DOMAIN", "163.com")

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/114.0.0.0 Safari/537.36"
)

REGISTER_PATH = "/api/v1/auth/register"
LOGIN_PATH = "/api/v1/auth/login"
SEND_CODE_PATH = "/api/v1/auth/send-code"
RESET_PASSWORD_PATH = "/api/v1/auth/reset-password"
BIND_EMAIL_PATH = "/api/v1/user/account/email"

_seq_lock = threading.Lock()
_seq_counter = itertools.count(1)


def _next_seq() -> int:
    with _seq_lock:
        return next(_seq_counter) + int(time.time() * 1000) % 1_000_000


def build_headers(referer_path: str) -> dict:
    return {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Content-Type": "application/json",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }


def build_auth_headers(referer_path: str, token: str) -> dict:
    headers = build_headers(referer_path)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_user_email(user) -> str:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    return f"load_{user_id}_{seq}@{EMAIL_DOMAIN}"


def build_register_payload(user) -> dict:
    """为每次注册请求生成唯一 username / phone，避免重复注册失败。"""
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"load_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"158{phone_suffix}"
    return {
        "username": username,
        "phone": phone,
        "password": DEFAULT_PASSWORD,
    }


def validate_api_response(res, checkpoint_prefix: str, *, expect_data: bool = False) -> bool:
    """
    通用检查点:
      1. HTTP 200
      2. body.code == 200
      3. 可选: data 字段存在
    """
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

    body = res.json()
    data = body.get("data") or {}
    token = data.get("access_token")
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


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "认证接口综合压测"

    测试机 = [
        {
            "ip地址": "192.168.22.28",
            "端口": 50000,
            "节点数": 1,
            "主节点": True,
        },
    ]


class 测试场景(WebScenario):
    """用户自定义场景信息"""

    场景名称 = "认证接口-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 50,
        "创建速率": 10,
        "运行时长": 300,
    }


class Transaction_Zhuce(SerialTransaction):
    """注册事务: POST /api/v1/auth/register"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "zhuce"

    def on_start(self):
        super().on_start()

    @task
    def task_0(self):
        user = self.parent.user if hasattr(self.parent, "user") else self.parent
        data = build_register_payload(user)
        url = f"{BASE_HOST}{REGISTER_PATH}"
        headers = build_headers("/register")

        with self.post(
            url,
            headers=headers,
            json=data,
            name="用户注册",
            catch_response=True,
        ) as res:
            validate_api_response(res, "注册")

    def on_stop(self):
        super().on_stop()


class Transaction_Denglu(SerialTransaction):
    """登录事务: POST /api/v1/auth/login（使用虚拟用户预热账号）"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "denglu"

    def on_start(self):
        super().on_start()

    @task
    def task_0(self):
        root_user = self._resolve_root_user()
        ctx = getattr(root_user, "_auth_ctx", None)
        if not ctx:
            msg = "登录跳过: 虚拟用户未预热注册"
            logger.warning(msg)
            return

        data = {
            "username": ctx["username"],
            "password": ctx["password"],
        }
        url = f"{BASE_HOST}{LOGIN_PATH}"
        headers = build_headers("/login")

        with self.post(
            url,
            headers=headers,
            json=data,
            name="用户登录",
            catch_response=True,
        ) as res:
            validate_login_response(res)

    def _resolve_root_user(self):
        node = self.parent
        while hasattr(node, "parent") and node.parent is not None:
            node = node.parent
        return node

    def on_stop(self):
        super().on_stop()


class Transaction_Wangjimima(SerialTransaction):
    """
    忘记密码事务（串行，邮箱）:
      1. send-code  — 向已绑定邮箱发送验证码（type=email, purpose=reset_password）
      2. reset-password — 可选，需 WR_DEV_VERIFY_CODE
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "wangjimima"

    def on_start(self):
        super().on_start()

    @task
    def task_0_send_code(self):
        root_user = self._resolve_root_user()
        ctx = getattr(root_user, "_auth_ctx", None)
        if not ctx or not ctx.get("email"):
            logger.warning("发送验证码跳过: 虚拟用户无绑定邮箱")
            return
        if not ctx.get("email_bound"):
            logger.warning("发送验证码跳过: 邮箱未绑定成功")
            return

        data = {
            "account": ctx["email"],
            "type": "email",
            "purpose": "reset_password",
        }
        url = f"{BASE_HOST}{SEND_CODE_PATH}"
        headers = build_headers("/forgot-password")

        with self.post(
            url,
            headers=headers,
            json=data,
            name="发送重置验证码",
            catch_response=True,
        ) as res:
            validate_api_response(res, "发送验证码")

    @task
    def task_1_reset_password(self):
        if not ENABLE_RESET_PASSWORD:
            logger.debug("reset-password 已禁用 (WR_ENABLE_RESET_PASSWORD=false)")
            return

        code = DEV_VERIFY_CODE
        if not code:
            logger.warning("reset-password 跳过: 未配置 WR_DEV_VERIFY_CODE")
            return

        root_user = self._resolve_root_user()
        ctx = getattr(root_user, "_auth_ctx", None)
        if not ctx:
            logger.warning("重置密码跳过: 虚拟用户未预热注册")
            return

        data = {
            "account": ctx["email"],
            "code": code,
            "new_password": NEW_PASSWORD,
        }
        url = f"{BASE_HOST}{RESET_PASSWORD_PATH}"
        headers = build_headers("/forgot-password")

        with self.post(
            url,
            headers=headers,
            json=data,
            name="重置密码",
            catch_response=True,
        ) as res:
            validate_api_response(res, "重置密码")

    def _resolve_root_user(self):
        node = self.parent
        while hasattr(node, "parent") and node.parent is not None:
            node = node.parent
        return node

    def on_stop(self):
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """
    事务集合: 注册 / 登录 / 忘记密码，混合比 1:1:1。
    虚拟用户 on_start 会先注册一个预热账号，供登录与忘记密码事务使用。
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("zhuce")
    def task_zhuce(self):
        Transaction_Zhuce(self).run()

    @task(1)
    @transaction("denglu")
    def task_denglu(self):
        Transaction_Denglu(self).run()

    @task(1)
    @transaction("wangjimima")
    def task_wangjimima(self):
        Transaction_Wangjimima(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 启动时预热注册，再循环执行 Action。"""

    host = BASE_HOST
    tasks = [WebrunnerAction]

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._auth_ctx = None

    def on_start(self):
        super().on_start()
        self._warmup_register()
        self._warmup_login()
        self._warmup_bind_email()
        logger.info(
            f"虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"email={self._auth_ctx.get('email') if self._auth_ctx else 'N/A'}"
        )

    def _warmup_register(self):
        """每个虚拟用户预先注册一个账号，供登录/忘记密码压测使用。"""
        data = build_register_payload(self)
        url = f"{BASE_HOST}{REGISTER_PATH}"
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
                    "email": None,
                    "email_bound": False,
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
        url = f"{BASE_HOST}{LOGIN_PATH}"
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

    def _warmup_bind_email(self):
        if not self._auth_ctx or not self._auth_ctx.get("token"):
            return

        email = build_user_email(self)
        self._auth_ctx["email"] = email

        send_url = f"{BASE_HOST}{SEND_CODE_PATH}"
        send_data = {"account": email, "type": "email", "purpose": "bind"}
        with self.client.post(
            send_url,
            headers=build_headers("/forgot-password"),
            json=send_data,
            name="预热发送邮箱验证码",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "预热发送邮箱验证码"):
                logger.error(f"预热发送邮箱验证码失败: email={email}")
                return

        if not DEV_VERIFY_CODE:
            logger.warning(
                "邮箱绑定跳过: 未配置 WR_DEV_VERIFY_CODE，忘记密码 send-code 将因邮箱未注册而失败"
            )
            return

        bind_data = {"email": email, "verify_code": DEV_VERIFY_CODE}
        with self.client.put(
            f"{BASE_HOST}{BIND_EMAIL_PATH}",
            headers=build_auth_headers("/user-center", self._auth_ctx["token"]),
            json=bind_data,
            name="预热绑定邮箱",
            catch_response=True,
        ) as res:
            if validate_api_response(res, "预热绑定邮箱"):
                self._auth_ctx["email_bound"] = True
            else:
                logger.error(f"预热绑定邮箱失败: email={email}")
