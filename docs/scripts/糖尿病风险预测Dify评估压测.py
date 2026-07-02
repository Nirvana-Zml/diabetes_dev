# -*- coding: utf-8 -*-
"""
糖尿病风险预测 — Dify 评估压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

相对浏览器录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录 → 完善资料（年龄/性别供 Dify 使用）
  - assessmentId 从 assess 响应动态获取，不再写死 asmt_934c0f0cbc904949
  - 修正详情 URL：GET /api/v1/risk/{assessmentId}（录制中路径有误）
  - assess 客户端超时默认 150s，避免 WebRunner 20s 默认超时
  - 校验 Dify 返回：assessmentId / riskScore / riskLevel
  - 低并发场景预设（Dify 为瓶颈，勿用 50 用户）

事务 tangniaobingfengxianyuce:
  task_0  POST /api/v1/risk/assess   提交问卷（Dify，核心指标）
  task_1  GET  /api/v1/risk/{id}    评估报告详情

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_ASSESS_TIMEOUT      assess 超时秒数，默认 130（须为 int）
  WR_NETWORK_TIMEOUT     客户端超时秒数，默认 max(150, assess+20)
  WR_ROUND_SLEEP         每轮事务结束休眠秒数，默认 3
"""
import itertools
import os
import threading
import time
from typing import Dict, Optional

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
ASSESS_TIMEOUT = int(os.getenv("WR_ASSESS_TIMEOUT", "130"))
NETWORK_TIMEOUT = int(os.getenv("WR_NETWORK_TIMEOUT", str(max(ASSESS_TIMEOUT + 20, 150))))
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "3"))

REFERER = "/health-evaluation"

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
    username = f"dify_{user_id}_{seq}"
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


def store_assessment_id(node, assessment_id: Optional[str]) -> None:
    if not assessment_id:
        return
    root = resolve_root_user(node)
    ctx = getattr(root, "_auth_ctx", None)
    if ctx is not None:
        ctx["last_assessment_id"] = assessment_id
        root._auth_ctx = ctx


def extract_assessment_id_from_body(body: dict) -> Optional[str]:
    data = body.get("data") or {}
    if isinstance(data, dict):
        found = data.get("assessmentId") or data.get("assessment_id")
        if found:
            return found
    return None


def build_risk_assess_payload(user) -> dict:
    """与录制脚本及 RiskAssessRequest DTO 一致，按用户 id 微调部分字段。"""
    user_id = getattr(user, "id", id(user)) % 10
    return {
        "height": 170,
        "weight": 72 + (user_id % 5),
        "fastingGlucose": 5,
        "postprandialGlucose": 8,
        "randomGlucose": 6,
        "hba1c": 4,
        "systolicBp": 120,
        "diastolicBp": 80,
        "diabetesType": 9,
        "isPregnant": False,
        "familyHistory": False,
        "isInsulinTaken": False,
        "smoking": 0,
        "alcohol": 0,
        "exerciseFreq": 1,
        "dietType": "balanced",
        "testSource": 1,
        "medicalHistories": [
            {
                "diseaseName": "抑郁症",
                "diagnosedDate": "2026-07-01",
                "status": 1,
                "note": "",
            }
        ],
        "medications": [
            {
                "drugName": "二甲双胍",
                "dosage": "0.5",
                "frequencyDesc": "",
                "isInsulin": False,
                "status": 1,
            }
        ],
        "familyHistories": [
            {
                "relation": "父亲",
                "memberAge": 50,
                "isAlive": True,
                "diseaseName": "糖尿病",
                "diagnosedAge": 50,
                "isDiabetes": True,
                "note": "",
            }
        ],
    }


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


def validate_assess_response(res) -> bool:
    """校验 Dify 评估成功：assessmentId / riskScore / riskLevel。"""
    if not validate_api_response(res, "提交风险评估", expect_data=True):
        return False

    data = res.json().get("data") or {}
    assessment_id = data.get("assessmentId") or data.get("assessment_id")
    risk_score = data.get("riskScore") if "riskScore" in data else data.get("risk_score")
    risk_level = data.get("riskLevel") if "riskLevel" in data else data.get("risk_level")

    ok = True
    if not assessment_id or not str(assessment_id).startswith("asmt_"):
        msg = f"提交风险评估 assessmentId 无效: {assessment_id}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        ok = False

    if risk_score is None:
        msg = "提交风险评估 缺少 riskScore"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        ok = False

    if not risk_level:
        msg = "提交风险评估 缺少 riskLevel"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        ok = False

    try:
        wr.checkpoint("提交风险评估-assessmentId有效", bool(assessment_id))
        wr.checkpoint("提交风险评估-riskScore存在", risk_score is not None)
        wr.checkpoint("提交风险评估-riskLevel存在", bool(risk_level))
    except Exception:
        pass

    if ok and hasattr(res, "success"):
        res.success()
    return ok


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


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "糖尿病风险预测Dify评估压测"

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
    Dify 评估专项场景（低并发）。

    录制脚本默认 50 用户 / 10 创建速率不适合 Dify，此处改为 8 / 1。
    """

    场景名称 = "Dify评估-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 8,
        "创建速率": 1,
        "运行时长": 600,
    }


class Transaction_Tangniaobingfengxianyuce(SerialTransaction):
    """
    事务定义（与录制脚本 task 对应）:
      task_0  POST /api/v1/risk/assess
      task_1  GET  /api/v1/risk/{assessmentId}
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "tangniaobingfengxianyuce"

    def on_start(self):
        super().on_start()

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("提交风险评估跳过: 未登录")
            return

        root = resolve_root_user(self.parent)
        url = f"{BASE_HOST}/api/v1/risk/assess"
        headers = build_auth_headers(ctx["token"])
        data = build_risk_assess_payload(root)
        started = time.time()
        try:
            with self.post(
                url,
                headers=headers,
                json=data,
                name="提交风险评估",
                catch_response=True,
                timeout=ASSESS_TIMEOUT,
            ) as res:
                elapsed = time.time() - started
                logger.info(
                    f"提交风险评估耗时: {elapsed:.2f}s (timeout={ASSESS_TIMEOUT}s)"
                )
                if validate_assess_response(res):
                    store_assessment_id(
                        self.parent,
                        extract_assessment_id_from_body(res.json()),
                    )
        except Exception as exc:
            elapsed = time.time() - started
            msg = (
                f"提交风险评估异常（elapsed={elapsed:.2f}s, "
                f"assess={ASSESS_TIMEOUT}s, client={NETWORK_TIMEOUT}s）: {exc}"
            )
            logger.error(msg)
            try:
                wr.checkpoint("提交风险评估-请求成功", False)
            except Exception:
                pass

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("评估报告详情跳过: 未登录")
            return

        assessment_id = ctx.get("last_assessment_id")
        if not assessment_id:
            logger.warning("评估报告详情跳过: assess 未返回 assessmentId")
            return

        url = f"{BASE_HOST}/api/v1/risk/{assessment_id}"
        headers = build_auth_headers(ctx["token"])
        with self.get(
            url,
            headers=headers,
            name="评估报告详情",
            catch_response=True,
        ) as res:
            validate_api_response(res, "评估报告详情", expect_data=True)

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 仅 Dify 评估事务，混合比 100%。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("tangniaobingfengxianyuce")
    def task_tangniaobingfengxianyuce(self):
        Transaction_Tangniaobingfengxianyuce(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环执行 Dify 评估。"""

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
        self._warmup_profile()
        logger.info(
            f"Dify评估虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"assess_timeout={ASSESS_TIMEOUT}s, client_timeout={NETWORK_TIMEOUT}s"
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
                    "last_assessment_id": None,
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

    def _warmup_profile(self):
        """完善年龄/性别，后端 assess 会从用户资料读取并传给 Dify。"""
        if not self._auth_ctx or not self._auth_ctx.get("token"):
            return

        data = {
            "nickname": f"Dify_{self._auth_ctx['username'][-6:]}",
            "gender": 2,
            "birth_date": "1980-01-01",
        }
        url = f"{BASE_HOST}/api/v1/user/profile"
        headers = build_auth_headers(self._auth_ctx["token"], "/user-center")
        with self.client.put(
            url,
            headers=headers,
            json=data,
            name="预热完善资料",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "预热完善资料"):
                logger.warning("预热完善资料失败，Dify 评估可能缺少年龄/性别")

# --- END OF SCRIPT ---
