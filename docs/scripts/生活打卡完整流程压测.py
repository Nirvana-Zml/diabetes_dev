# -*- coding: utf-8 -*-
"""
生活打卡 — 场景 B：完整打卡流程（读写）压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

场景 B 流程（与 CheckinRecords 预设打卡 + 提交后刷新一致）:
  读:  today → food/medication/exercise presets
  写:  POST food → POST medication → POST exercise → POST glucose
  读:  today（刷新）→ 四类 records?date=

相对录制脚本的改进:
  - 去除硬编码 JWT，虚拟用户预热：注册 → 登录
  - 去除重复 today 请求，补全 4 类写接口
  - 打卡日期动态取当天；餐次/血糖值按轮次微调，避免同用户循环冲突
  - 统一 HTTP/业务码检查点（catch_response + wr.checkpoint）

配套脚本:
  场景 A 只读页面加载 → docs/scripts/生活打卡页面加载压测.py

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_FOOD_CATEGORY_ID    食物预设分类，默认 cat_grain
  WR_CHECKIN_DATE        打卡/查询日期 YYYY-MM-DD，默认当天
"""
import itertools
import os
import threading
import time
from datetime import date
from typing import Dict, Optional

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
FOOD_CATEGORY_ID = os.getenv("WR_FOOD_CATEGORY_ID", "cat_grain")
CHECKIN_DATE = os.getenv("WR_CHECKIN_DATE", date.today().isoformat())

REFERER = "/checkin-records"

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
    username = f"chk_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"157{phone_suffix}"
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


def bump_round(node) -> int:
    root = resolve_root_user(node)
    ctx = getattr(root, "_auth_ctx", None) or {}
    current = int(ctx.get("round", 0)) + 1
    ctx["round"] = current
    root._auth_ctx = ctx
    return current


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


def validate_checkin_write_response(res, checkpoint_prefix: str) -> bool:
    if not validate_api_response(res, checkpoint_prefix, expect_data=True):
        return False

    data = res.json().get("data") or {}
    checkin_id = data.get("checkinId") or data.get("checkin_id")
    try:
        wr.checkpoint(f"{checkpoint_prefix}-checkinId存在", bool(checkin_id))
    except Exception:
        pass
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


class _CheckinFlowMixin:
    """打卡读写公共逻辑。"""

    parent: "User"

    def _get_checkin(self, path: str, *, name: str, params: Optional[dict] = None) -> bool:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning(f"{name}跳过: 未登录")
            return False

        url = f"{BASE_HOST}{path}"
        headers = build_auth_headers(ctx["token"])
        with self.get(
            url,
            headers=headers,
            params=params or {},
            name=name,
            catch_response=True,
        ) as res:
            return validate_api_response(res, name, expect_data=True)

    def _post_checkin(self, path: str, *, name: str, data: dict) -> bool:
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning(f"{name}跳过: 未登录")
            return False

        url = f"{BASE_HOST}{path}"
        headers = build_auth_headers(ctx["token"])
        with self.post(
            url,
            headers=headers,
            json=data,
            name=name,
            catch_response=True,
        ) as res:
            return validate_checkin_write_response(res, name)


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "生活打卡完整流程压测"

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
    读写混合场景，写接口占比较高，默认略低于纯只读场景。
    若失败率升高，可降为 用户数 20、创建速率 5。
    """

    场景名称 = "生活打卡-完整流程-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 30,
        "创建速率": 5,
        "运行时长": 300,
    }


class Transaction_Shenghuodaka(_CheckinFlowMixin, SerialTransaction):
    """
    场景 B — 完整打卡流程（读写）:
      读 today + presets
      → 写 food/medication/exercise/glucose
      → 读 today + 四类 records
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "shenghuodaka"

    def on_start(self):
        bump_round(self.parent)
        super().on_start()

    @task
    def task_0(self):
        self._get_checkin("/api/v1/checkin/today", name="今日打卡状态")

    @task
    def task_1(self):
        self._get_checkin(
            "/api/v1/checkin/food/presets",
            name="食物预设",
            params={"categoryId": FOOD_CATEGORY_ID},
        )

    @task
    def task_2(self):
        self._get_checkin("/api/v1/checkin/medication/presets", name="用药预设")

    @task
    def task_3(self):
        self._get_checkin("/api/v1/checkin/exercise/presets", name="运动预设")

    @task
    def task_4(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return
        round_no = int(ctx.get("round", 1))
        meal_period = 1 + (round_no - 1) % 3
        self._post_checkin(
            "/api/v1/checkin/food",
            name="食物打卡",
            data={
                "checkinDate": CHECKIN_DATE,
                "mealPeriod": meal_period,
                "sourceType": 1,
                "foodId": "food_rice_001",
                "inputUnit": 1,
                "inputAmount": 100,
                "mlToGRatio": 1,
                "imageObjectKey": "food/food_rice_001.jpg",
            },
        )

    @task
    def task_5(self):
        self._post_checkin(
            "/api/v1/checkin/medication",
            name="用药打卡",
            data={
                "checkinDate": CHECKIN_DATE,
                "sourceType": 1,
                "drugId": "drug_metformin_001",
                "dosage": "0.5",
                "taken": True,
                "imageObjectKey": "medical/drug_metformin_001.jpg",
            },
        )

    @task
    def task_6(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return
        round_no = int(ctx.get("round", 1))
        self._post_checkin(
            "/api/v1/checkin/exercise",
            name="运动打卡",
            data={
                "checkinDate": CHECKIN_DATE,
                "sourceType": 1,
                "exerciseId": "ex_walk_001",
                "durationMinutes": 20 + (round_no % 4) * 5,
            },
        )

    @task
    def task_7(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return
        root = resolve_root_user(self.parent)
        user_id = getattr(root, "id", id(root)) % 10
        round_no = int(ctx.get("round", 1))
        glucose_value = round(5.0 + (user_id % 5) * 0.2 + (round_no % 3) * 0.1, 1)
        self._post_checkin(
            "/api/v1/checkin/glucose",
            name="血糖打卡",
            data={
                "checkinDate": CHECKIN_DATE,
                "glucoseValue": glucose_value,
                "measureContext": 4,
                "unit": 1,
            },
        )

    @task
    def task_8(self):
        self._get_checkin("/api/v1/checkin/today", name="今日状态-提交后刷新")

    @task
    def task_9(self):
        self._get_checkin(
            "/api/v1/checkin/food/records",
            name="食物打卡记录",
            params={"date": CHECKIN_DATE},
        )

    @task
    def task_10(self):
        self._get_checkin(
            "/api/v1/checkin/medication/records",
            name="用药打卡记录",
            params={"date": CHECKIN_DATE},
        )

    @task
    def task_11(self):
        self._get_checkin(
            "/api/v1/checkin/exercise/records",
            name="运动打卡记录",
            params={"date": CHECKIN_DATE},
        )

    @task
    def task_12(self):
        self._get_checkin(
            "/api/v1/checkin/glucose/records",
            name="血糖打卡记录",
            params={"date": CHECKIN_DATE},
        )

    def on_stop(self):
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 完整打卡读写事务，混合比 100%。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("shenghuodaka")
    def task_shenghuodaka(self):
        Transaction_Shenghuodaka(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环执行完整打卡流程。"""

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
            f"打卡流程虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"username={self._auth_ctx['username'] if self._auth_ctx else 'N/A'}, "
            f"checkin_date={CHECKIN_DATE}"
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

# --- END OF SCRIPT ---
