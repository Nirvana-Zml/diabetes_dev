# -*- coding: utf-8 -*-
"""
糖尿病风险预测压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

相对浏览器录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录 → 完善资料（年龄/性别）
  - 注册 username/phone 参数化，避免并发冲突
  - 统一 HTTP/业务码检查点（catch_response + wr.checkpoint）
  - assessmentId 从 assess 响应或 history 动态获取
  - assess 客户端超时默认 140s（避免 20s 默认超时）
  - 事务拆分，避免 Dify assess 失败拖垮整笔事务

事务（原 tangniaobingfengxianyuce 拆分为）:
  - tangniaobingfengxianyuce_jiazai  页面加载（轻量，高并发）
      GET profile → GET health-records/latest → GET risk/history → GET risk/{id}（可选）
  - tangniaobingfengxianyuce_pinggu  提交评估（重量，低混合比）
      POST risk/assess → GET risk/history → GET risk/{id}

推荐压测方式:
  1. 页面加载（默认）: 不配置任何环境变量，50 用户压 jiazai
  2. Dify 评估专项: 使用同目录「糖尿病风险预测Dify评估压测.py」（低并发预设）

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_ENABLE_ASSESS       是否压 assess，默认 false
  WR_PAGE_TASK_WEIGHT    页面事务混合比，默认 10
  WR_ASSESS_TASK_WEIGHT  评估事务混合比，默认 1（ENABLE_ASSESS=false 时为 0）
  WR_ASSESS_TIMEOUT      assess 超时秒数，默认 130（须为 int）
  WR_NETWORK_TIMEOUT     客户端超时秒数，默认 max(140, assess+10)
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
ENABLE_ASSESS = os.getenv("WR_ENABLE_ASSESS", "false").lower() == "true"
ASSESS_TIMEOUT = int(os.getenv("WR_ASSESS_TIMEOUT", "130"))
NETWORK_TIMEOUT = int(os.getenv("WR_NETWORK_TIMEOUT", str(max(ASSESS_TIMEOUT + 10, 140))))
PAGE_TASK_WEIGHT = int(os.getenv("WR_PAGE_TASK_WEIGHT", "10"))
ASSESS_TASK_WEIGHT = int(os.getenv("WR_ASSESS_TASK_WEIGHT", "1" if ENABLE_ASSESS else "0"))

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
    username = f"load_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"158{phone_suffix}"
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
        records = data.get("records") or data.get("list") or []
        if records:
            first = records[0]
            return first.get("assessmentId") or first.get("assessment_id")
    return None


def build_risk_assess_payload() -> dict:
    """与前端 HealthEvaluation 问卷及 RiskAssessRequest DTO 一致。"""
    return {
        "height": 170,
        "weight": 72,
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


class _RiskApiMixin:
    """风险预测接口公共步骤。"""

    parent: "User"

    def _load_risk_history(self, *, request_name: str = "评估历史") -> bool:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning(f"{request_name}跳过: 未登录")
            return False

        url = f"{BASE_HOST}/api/v1/risk/history"
        headers = build_auth_headers(ctx["token"])
        params = {"page": 1, "size": 10}
        with self.get(
            url,
            headers=headers,
            params=params,
            name=request_name,
            catch_response=True,
        ) as res:
            if validate_api_response(res, request_name, expect_data=True):
                store_assessment_id(self.parent, extract_assessment_id_from_body(res.json()))
                return True
        return False

    def _load_risk_detail(self, *, optional: bool = False) -> bool:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("评估详情跳过: 未登录")
            return False

        assessment_id = ctx.get("last_assessment_id")
        if not assessment_id:
            if optional:
                logger.debug("评估详情跳过: 暂无历史记录（可选步骤）")
                return True
            logger.warning("评估详情跳过: 无 assessmentId")
            return False

        url = f"{BASE_HOST}/api/v1/risk/{assessment_id}"
        headers = build_auth_headers(ctx["token"])
        with self.get(url, headers=headers, name="评估报告详情", catch_response=True) as res:
            return validate_api_response(res, "评估报告详情", expect_data=True)

    def _submit_risk_assess(self) -> bool:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("风险评估跳过: 未登录")
            return False

        url = f"{BASE_HOST}/api/v1/risk/assess"
        headers = build_auth_headers(ctx["token"])
        data = build_risk_assess_payload()
        try:
            with self.post(
                url,
                headers=headers,
                json=data,
                name="提交风险评估",
                catch_response=True,
                timeout=ASSESS_TIMEOUT,
            ) as res:
                if validate_api_response(res, "提交风险评估", expect_data=True):
                    store_assessment_id(self.parent, extract_assessment_id_from_body(res.json()))
                    return True
                return False
        except Exception as exc:
            msg = (
                f"提交风险评估异常（assess={ASSESS_TIMEOUT}s, client={NETWORK_TIMEOUT}s）: {exc}"
            )
            logger.error(msg)
            try:
                wr.checkpoint("提交风险评估-请求成功", False)
            except Exception:
                pass
            return False


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "糖尿病风险预测压测"

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
    用户自定义场景信息。

    页面加载压测: 保持默认参数（50 用户 / 10 创建速率 / 300s）。
    评估专项压测: 建议改为 用户数 5~10、创建速率 1~2，并设置 WR_ENABLE_ASSESS=true。
    """

    场景名称 = "风险预测-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 50,
        "创建速率": 10,
        "运行时长": 300,
    }


class Transaction_Tangniaobingfengxianyuce_Jiazai(_RiskApiMixin, SerialTransaction):
    """
    页面加载事务（对应录制脚本 task_0~task_2，并补充详情查询）:
      profile → health-records/latest → history → detail（有历史才查）
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "tangniaobingfengxianyuce_jiazai"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("用户资料跳过: 未登录")
            return

        url = f"{BASE_HOST}/api/v1/user/profile"
        headers = build_auth_headers(ctx["token"])
        with self.get(url, headers=headers, name="用户资料", catch_response=True) as res:
            validate_api_response(res, "用户资料", expect_data=True)

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("健康档案跳过: 未登录")
            return

        url = f"{BASE_HOST}/api/v1/health-records/latest"
        headers = build_auth_headers(ctx["token"])
        with self.get(url, headers=headers, name="最新健康档案", catch_response=True) as res:
            # 无档案时 data 为空对象 {}，仍返回 code=200
            validate_api_response(res, "最新健康档案", expect_data=True)

    @task
    def task_2(self):
        self._load_risk_history()

    @task
    def task_3(self):
        self._load_risk_detail(optional=True)


class Transaction_Tangniaobingfengxianyuce_Pinggu(_RiskApiMixin, SerialTransaction):
    """
    提交评估事务（重量，Dify）:
      assess → history → detail
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "tangniaobingfengxianyuce_pinggu"

    @task
    def task_0(self):
        self._submit_risk_assess()

    @task
    def task_1(self):
        self._load_risk_history(request_name="评估历史-提交后刷新")

    @task
    def task_2(self):
        self._load_risk_detail(optional=False)


class WebrunnerAction(BrowserAction):
    """
    事务集合。混合比:
      页面加载  WR_PAGE_TASK_WEIGHT（默认 10）
      提交评估  WR_ASSESS_TASK_WEIGHT（默认 1；WR_ENABLE_ASSESS=false 时为 0）
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(PAGE_TASK_WEIGHT)
    @transaction("tangniaobingfengxianyuce_jiazai")
    def task_jiazai(self):
        Transaction_Tangniaobingfengxianyuce_Jiazai(self).run()

    @task(ASSESS_TASK_WEIGHT)
    @transaction("tangniaobingfengxianyuce_pinggu")
    def task_pinggu(self):
        Transaction_Tangniaobingfengxianyuce_Pinggu(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环执行 Action。"""

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
            f"虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"enable_assess={ENABLE_ASSESS}, page_weight={PAGE_TASK_WEIGHT}, "
            f"assess_weight={ASSESS_TASK_WEIGHT}"
        )

    def _apply_client_timeout(self):
        for attr in ("network_timeout", "connection_timeout"):
            if hasattr(self.client, attr):
                setattr(self.client, attr, NETWORK_TIMEOUT)
        logger.debug(
            f"HTTP 客户端超时: network={NETWORK_TIMEOUT}s, assess={ASSESS_TIMEOUT}s"
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
        if not self._auth_ctx or not self._auth_ctx.get("token"):
            return

        data = {
            "nickname": f"Risk_{self._auth_ctx['username'][-6:]}",
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
                logger.warning("预热完善资料失败，评估可能缺少年龄/性别")

# --- END OF SCRIPT ---
