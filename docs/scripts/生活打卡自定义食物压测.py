# -*- coding: utf-8 -*-
"""
生活打卡 — 自定义食物上传打卡压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

事务 zidingyishiwushangchuan（与录制 task 顺序一致）:
  task_0  POST /checkin/upload-image?type=food   上传食物图片
  task_1  POST /checkin/food                     自定义食物打卡（sourceType=2）
  task_2  GET  /checkin/food/records?date=
  task_3  GET  /checkin/achievements
  task_4  GET  /checkin/stats?range=monthly
  task_5  GET  /checkin/medication/records?date=
  task_6  GET  /checkin/exercise/records?date=
  task_7  GET  /checkin/glucose/records?date=

相对录制脚本的改进:
  - 去除硬编码 JWT / 固定 imageObjectKey，upload 响应 objectKey 动态传给 food
  - 空 multipart 改为最小 SVG 上传（避免 HAR 对 JPEG 二进制 UTF-8 解码报错）
  - 打卡日期动态取当天；foodName 按轮次微调（默认「油条」）
  - 统一 catch_response + wr.checkpoint 响应校验

说明:
  - 录制中 upload_xxx.jpg 为 MinIO 图片预览 URL，非 API，压测跳过
  - 上传使用最小 SVG（image/svg+xml），避免 WebRunner 调试 HAR 对 JPEG 二进制 UTF-8 解码报错
  - 含文件上传，建议场景 20 用户 / 3 创建速率（勿用 50/10）

环境变量（均可选）:
  WR_BASE_HOST           默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD    默认 Test123456
  WR_CHECKIN_DATE        打卡日期 YYYY-MM-DD，默认当天
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
CHECKIN_DATE = os.getenv("WR_CHECKIN_DATE", date.today().isoformat())

REFERER = "/checkin-records"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/114.0.0.0 Safari/537.36"
)

# 最小 SVG（纯 UTF-8），后端校验 contentType.startswith("image/") 可通过；
# 避免 JPEG 二进制导致 WebRunner debug HAR 报 UnicodeDecodeError（接口本身仍 200）。
MINIMAL_SVG = b'<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>'
UPLOAD_CONTENT_TYPE = "image/svg+xml"

_seq_lock = threading.Lock()
_seq_counter = itertools.count(1)


def _next_seq() -> int:
    with _seq_lock:
        return next(_seq_counter) + int(time.time() * 1000) % 1_000_000


def resolve_root_user(node):
    while hasattr(node, "parent") and node.parent is not None:
        node = node.parent
    return node


def build_headers(referer_path: str = REFERER, *, json_body: bool = True) -> dict:
    headers = {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }
    if json_body:
        headers["Content-Type"] = "application/json"
    return headers


def build_auth_headers(token: str, referer_path: str = REFERER, *, json_body: bool = True) -> dict:
    headers = build_headers(referer_path, json_body=json_body)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_register_payload(user) -> dict:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"food_{user_id}_{seq}"
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


class _CustomFoodMixin:
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


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "生活打卡自定义食物压测"

    测试机 = [
        {
            "ip地址": "192.168.22.28",
            "端口": 50000,
            "节点数": 1,
            "主节点": True,
        },
    ]


class 测试场景(WebScenario):
    """含 multipart 上传，并发不宜过高。"""

    场景名称 = "自定义食物打卡-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 20,
        "创建速率": 3,
        "运行时长": 300,
    }


class Transaction_Zidingyishiwushangchuan(_CustomFoodMixin, SerialTransaction):
    """自定义食物上传打卡（task_0~task_7 与录制脚本一致）。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "zidingyishiwushangchuan"

    def on_start(self):
        bump_round(self.parent)
        super().on_start()

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("上传打卡图片跳过: 未登录")
            return

        url = f"{BASE_HOST}/api/v1/checkin/upload-image"
        headers = build_auth_headers(ctx["token"], json_body=False)
        files = {
            "file": (
                f"food_{_next_seq()}.svg",
                MINIMAL_SVG,
                UPLOAD_CONTENT_TYPE,
            )
        }
        with self.post(
            url,
            headers=headers,
            params={"type": "food"},
            files=files,
            name="上传打卡图片",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "上传打卡图片", expect_data=True):
                return
            data = res.json().get("data") or {}
            object_key = data.get("objectKey") or data.get("object_key")
            if not object_key:
                msg = "上传打卡图片 缺少 objectKey"
                logger.warning(msg)
                if hasattr(res, "failure"):
                    res.failure(msg)
                return
            ctx["last_food_image_key"] = object_key
            resolve_root_user(self.parent)._auth_ctx = ctx
            logger.debug(f"上传打卡图片 objectKey={object_key}")

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("自定义食物打卡跳过: 未登录")
            return

        object_key = ctx.get("last_food_image_key")
        if not object_key:
            logger.warning("自定义食物打卡跳过: 无 imageObjectKey（请先成功 upload）")
            return

        round_no = int(ctx.get("round", 1))
        url = f"{BASE_HOST}/api/v1/checkin/food"
        headers = build_auth_headers(ctx["token"])
        data = {
            "checkinDate": CHECKIN_DATE,
            "mealPeriod": 1 + (round_no - 1) % 3,
            "sourceType": 2,
            "categoryId": "cat_grain",
            "foodName": f"油条_{round_no}" if round_no > 1 else "油条",
            "caloriesPerGram": 3,
            "inputUnit": 1,
            "inputAmount": 100,
            "mlToGRatio": 1,
            "imageObjectKey": object_key,
        }
        with self.post(
            url,
            headers=headers,
            json=data,
            name="自定义食物打卡",
            catch_response=True,
        ) as res:
            validate_checkin_write_response(res, "自定义食物打卡")

    @task
    def task_2(self):
        self._get_checkin(
            "/api/v1/checkin/food/records",
            name="食物打卡记录",
            params={"date": CHECKIN_DATE},
        )

    @task
    def task_3(self):
        self._get_checkin("/api/v1/checkin/achievements", name="打卡成就")

    @task
    def task_4(self):
        self._get_checkin(
            "/api/v1/checkin/stats",
            name="打卡统计",
            params={"range": "monthly"},
        )

    @task
    def task_5(self):
        self._get_checkin(
            "/api/v1/checkin/medication/records",
            name="用药打卡记录",
            params={"date": CHECKIN_DATE},
        )

    @task
    def task_6(self):
        self._get_checkin(
            "/api/v1/checkin/exercise/records",
            name="运动打卡记录",
            params={"date": CHECKIN_DATE},
        )

    @task
    def task_7(self):
        self._get_checkin(
            "/api/v1/checkin/glucose/records",
            name="血糖打卡记录",
            params={"date": CHECKIN_DATE},
        )

    def on_stop(self):
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: 自定义食物打卡，混合比 100%。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("zidingyishiwushangchuan")
    def task_zidingyishiwushangchuan(self):
        Transaction_Zidingyishiwushangchuan(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环执行自定义食物打卡。"""

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
            f"自定义食物虚拟用户启动: id={getattr(self, 'id', id(self))}, "
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
                    "last_food_image_key": None,
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
