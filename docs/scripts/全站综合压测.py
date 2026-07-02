# -*- coding: utf-8 -*-
"""
糖尿病健康管理平台 — 全站综合压测脚本（WebRunner）

基于浏览器录制事务完善，主要改进:
  - 虚拟用户启动时自动「预热注册 + 登录」，动态维护 access_token
  - 注册/登录/手机号等参数化，避免并发重复数据导致失败
  - 统一 HTTP/业务码检查点（wr.checkpoint + catch_response）
  - 静态 JWT、固定 article/session 等改为运行时从接口获取
  - 头像/打卡图片上传使用最小合法 JPEG，替代空 multipart
  - 忘记密码使用邮箱（type=email, purpose=reset_password），预热阶段绑定唯一邮箱
  - 忘记密码默认仅压 send-code；完整 reset 需 WR_DEV_VERIFY_CODE
  - AI 类接口（问诊消息、打卡 AI 分析）耗时较长，可按需调低 @task 权重

环境变量（可选）:
  WR_BASE_HOST              默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD       默认 Test123456
  WR_NEW_PASSWORD           重置密码新密码，默认 Test654321
  WR_ENABLE_RESET_PASSWORD  true 时压测 reset-password
  WR_DEV_VERIFY_CODE        邮箱绑定/重置密码用验证码（6 位，需与邮件或测试配置一致）
  WR_EMAIL_DOMAIN           压测邮箱域名，默认 163.com
  WR_AI_DOCTOR_ID           默认 doc_002
"""
import base64
import itertools
import os
import threading
import time
from datetime import date, timedelta

from loguru import logger
from webrunnercore import wr  # wr 模块是 webrunner 内置的脚本增强 sdk
from webrunnercore import *


# ---------------------------------------------------------------------------
# 全局配置
# ---------------------------------------------------------------------------
BASE_HOST = os.getenv("WR_BASE_HOST", "http://192.168.22.28")
DEFAULT_PASSWORD = os.getenv("WR_DEFAULT_PASSWORD", "Test123456")
NEW_PASSWORD = os.getenv("WR_NEW_PASSWORD", "Test654321")
ENABLE_RESET_PASSWORD = os.getenv("WR_ENABLE_RESET_PASSWORD", "false").lower() == "true"
DEV_VERIFY_CODE = os.getenv("WR_DEV_VERIFY_CODE", "")
EMAIL_DOMAIN = os.getenv("WR_EMAIL_DOMAIN", "163.com")
AI_DOCTOR_ID = os.getenv("WR_AI_DOCTOR_ID", "doc_002")

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/114.0.0.0 Safari/537.36"
)

# 1x1 像素 JPEG，用于头像/打卡图片上传压测
MINIMAL_JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////"
    "2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB"
    "/8QAFQABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGfx//EABQQAQAAAAAAAAAAAAAAAAAAAAD/"
    "2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEGBj8Bf//"
    "EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAByj8f//Z"
)

_seq_lock = threading.Lock()
_seq_counter = itertools.count(1)


def _next_seq() -> int:
    with _seq_lock:
        return next(_seq_counter) + int(time.time() * 1000) % 1_000_000


def _today() -> str:
    return date.today().isoformat()


def _date_range(days: int = 29) -> tuple[str, str]:
    end = date.today()
    start = end - timedelta(days=days)
    return start.isoformat(), end.isoformat()


def resolve_root_user(node):
    while hasattr(node, "parent") and node.parent is not None:
        node = node.parent
    return node


def build_headers(referer_path: str, *, json_body: bool = True) -> dict:
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


def build_auth_headers(referer_path: str, token: str, *, json_body: bool = True) -> dict:
    headers = build_headers(referer_path, json_body=json_body)
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


def build_user_email(user) -> str:
    """为虚拟用户生成唯一邮箱，供忘记密码（邮箱 reset）压测使用。"""
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    return f"load_{user_id}_{seq}@{EMAIL_DOMAIN}"


def get_auth_ctx(node) -> dict | None:
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


def validate_http_ok(res, checkpoint_prefix: str, *, accept_status=(200, 206)) -> bool:
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


def build_default_reminder_rules() -> dict:
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


def build_reminder_rules_variant() -> dict:
    rules = build_default_reminder_rules()["rules"]
    rules[1]["remindTime"] = "23:20"
    return {"rules": rules}


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "全站综合压测"

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

    场景名称 = "全站综合-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 50,
        "创建速率": 10,
        "运行时长": 300,
    }


# ---------------------------------------------------------------------------
# 事务定义
# ---------------------------------------------------------------------------


class Transaction_Morenfenzu(SerialTransaction):
    """进入首页: GET /home"""

    @property
    def transaction(self):
        return "morenfenzu"

    @task
    def task_0(self):
        url = f"{BASE_HOST}/home"
        headers = {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9",
            "User-Agent": USER_AGENT,
        }
        with self.get(url, headers=headers, name="进入首页", catch_response=True) as res:
            validate_http_ok(res, "首页")


class Transaction_Jinrushouye(SerialTransaction):
    """首页数据: GET /api/v2/home/content（替代静态 .vue 资源请求）"""

    @property
    def transaction(self):
        return "jinrushouye"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        headers = build_auth_headers("/home", ctx["token"]) if ctx else build_headers("/home")
        url = f"{BASE_HOST}/api/v2/home/content"
        with self.get(url, headers=headers, name="首页内容", catch_response=True) as res:
            validate_api_response(res, "首页内容", expect_data=True)


class Transaction_Zhuce(SerialTransaction):
    """注册: POST /api/v1/auth/register"""

    @property
    def transaction(self):
        return "zhuce"

    @task
    def task_0(self):
        user = resolve_root_user(self.parent)
        data = build_register_payload(user)
        url = f"{BASE_HOST}/api/v1/auth/register"
        headers = build_headers("/register")
        with self.post(url, headers=headers, json=data, name="用户注册", catch_response=True) as res:
            validate_api_response(res, "注册")


class Transaction_Denglu(SerialTransaction):
    """登录: POST /api/v1/auth/login"""

    @property
    def transaction(self):
        return "denglu"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("登录跳过: 虚拟用户未预热")
            return

        data = {"username": ctx["username"], "password": ctx["password"]}
        url = f"{BASE_HOST}/api/v1/auth/login"
        headers = build_headers("/login")
        with self.post(url, headers=headers, json=data, name="用户登录", catch_response=True) as res:
            if validate_login_response(res):
                token = res.json()["data"]["access_token"]
                ctx["token"] = token
                resolve_root_user(self.parent)._auth_ctx = ctx


class Transaction_Wangjimima(SerialTransaction):
    """忘记密码: 邮箱 send-code + 可选 reset-password（与前端 ForgotPassword 一致）"""

    @property
    def transaction(self):
        return "wangjimima"

    @task
    def task_0(self):
        ctx = getattr(resolve_root_user(self.parent), "_auth_ctx", None)
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
        url = f"{BASE_HOST}/api/v1/auth/send-code"
        headers = build_headers("/forgot-password")
        with self.post(url, headers=headers, json=data, name="发送重置验证码", catch_response=True) as res:
            validate_api_response(res, "发送验证码")

    @task
    def task_1(self):
        if not ENABLE_RESET_PASSWORD:
            return
        if not DEV_VERIFY_CODE:
            logger.warning("reset-password 跳过: 未配置 WR_DEV_VERIFY_CODE")
            return

        ctx = getattr(resolve_root_user(self.parent), "_auth_ctx", None)
        if not ctx or not ctx.get("email"):
            return

        data = {
            "account": ctx["email"],
            "code": DEV_VERIFY_CODE,
            "new_password": NEW_PASSWORD,
        }
        url = f"{BASE_HOST}/api/v1/auth/reset-password"
        headers = build_headers("/forgot-password")
        with self.post(url, headers=headers, json=data, name="重置密码", catch_response=True) as res:
            validate_api_response(res, "重置密码")


class Transaction_Gerenxinxixiugai(SerialTransaction):
    """个人中心: 头像上传 + 资料修改"""

    @property
    def transaction(self):
        return "gerenxinxixiugai"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/user/avatar"
        headers = build_auth_headers("/user-center", ctx["token"], json_body=False)
        files = {"file": ("avatar.jpg", MINIMAL_JPEG, "image/jpeg")}
        with self.post(url, headers=headers, files=files, name="上传头像", catch_response=True) as res:
            validate_api_response(res, "上传头像")

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        data = {
            "nickname": f"Load_{ctx['username'][-6:]}",
            "gender": 2,
            "birth_date": "2005-11-01",
        }
        url = f"{BASE_HOST}/api/v1/user/profile"
        headers = build_auth_headers("/user-center", ctx["token"])
        with self.put(url, headers=headers, json=data, name="修改资料", catch_response=True) as res:
            validate_api_response(res, "修改资料")


class Transaction_Tangniaobingfengxianyuce(SerialTransaction):
    """首页 AI 医生与内容"""

    @property
    def transaction(self):
        return "tangniaobingfengxianyuce"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v2/ai-doctors"
        headers = build_auth_headers("/home", ctx["token"])
        with self.get(url, headers=headers, name="AI医生列表", catch_response=True) as res:
            validate_api_response(res, "AI医生列表", expect_data=True)

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v2/home/content"
        headers = build_auth_headers("/home", ctx["token"])
        with self.get(url, headers=headers, name="首页内容", catch_response=True) as res:
            validate_api_response(res, "首页内容", expect_data=True)


class Transaction_Shenghuodaka(SerialTransaction):
    """生活打卡: 预设食物 + 用药"""

    @property
    def transaction(self):
        return "shenghuodaka"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        data = {
            "checkinDate": _today(),
            "mealPeriod": 1,
            "sourceType": 1,
            "foodId": "food_rice_001",
            "inputUnit": 1,
            "inputAmount": 100,
            "mlToGRatio": 1,
            "imageObjectKey": "food/food_rice_001.jpg",
        }
        url = f"{BASE_HOST}/api/v1/checkin/food"
        headers = build_auth_headers("/checkin-records", ctx["token"])
        with self.post(url, headers=headers, json=data, name="食物打卡", catch_response=True) as res:
            validate_api_response(res, "食物打卡")

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        data = {
            "checkinDate": _today(),
            "sourceType": 1,
            "drugId": "drug_metformin_001",
            "dosage": "0.5",
            "taken": True,
            "imageObjectKey": "medical/drug_metformin_001.jpg",
        }
        url = f"{BASE_HOST}/api/v1/checkin/medication"
        headers = build_auth_headers("/checkin-records", ctx["token"])
        with self.post(url, headers=headers, json=data, name="用药打卡", catch_response=True) as res:
            validate_api_response(res, "用药打卡")


class Transaction_Zidingyishiwushangchuan(SerialTransaction):
    """自定义食物: 上传图片 + 手动录入"""

    @property
    def transaction(self):
        return "zidingyishiwushangchuan"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/checkin/upload-image"
        headers = build_auth_headers("/checkin-records", ctx["token"], json_body=False)
        files = {"file": ("food.jpg", MINIMAL_JPEG, "image/jpeg")}
        params = {"type": "food"}
        with self.post(
            url,
            headers=headers,
            params=params,
            files=files,
            name="上传打卡图片",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "上传打卡图片", expect_data=True):
                return
            object_key = (res.json().get("data") or {}).get("objectKey") or (
                res.json().get("data") or {}
            ).get("object_key")
            ctx["last_food_image_key"] = object_key or f"food/upload_{_next_seq()}.jpg"
            resolve_root_user(self.parent)._auth_ctx = ctx

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        data = {
            "checkinDate": _today(),
            "mealPeriod": 2,
            "sourceType": 2,
            "categoryId": "cat_grain",
            "foodName": "油条",
            "caloriesPerGram": 3,
            "inputUnit": 1,
            "inputAmount": 100,
            "mlToGRatio": 1,
            "imageObjectKey": ctx.get("last_food_image_key", "food/custom.jpg"),
        }
        url = f"{BASE_HOST}/api/v1/checkin/food"
        headers = build_auth_headers("/checkin-records", ctx["token"])
        with self.post(url, headers=headers, json=data, name="自定义食物打卡", catch_response=True) as res:
            validate_api_response(res, "自定义食物打卡")


class Transaction_Dakaxingweiaifenxi(SerialTransaction):
    """打卡行为 AI 分析（替代静态 JS 资源请求）"""

    @property
    def transaction(self):
        return "dakaxingweiAIfenxi"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        start_date, end_date = _date_range()
        url = f"{BASE_HOST}/api/v1/checkin-management/ai-summary"
        headers = build_auth_headers("/checkin-analysis", ctx["token"])
        params = {"startDate": start_date, "endDate": end_date}
        with self.get(
            url,
            headers=headers,
            params=params,
            name="打卡AI分析",
            catch_response=True,
            timeout=180,
        ) as res:
            validate_api_response(res, "打卡AI分析", expect_data=True)


class Transaction_Dakatixing(SerialTransaction):
    """打卡提醒规则: 查询 + 更新"""

    @property
    def transaction(self):
        return "dakatixing"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/checkin/reminders/rules"
        headers = build_auth_headers("/checkin-reminder-settings", ctx["token"])
        with self.get(url, headers=headers, name="查询提醒规则", catch_response=True) as res:
            validate_api_response(res, "查询提醒规则", expect_data=True)

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/checkin/reminders/rules"
        headers = build_auth_headers("/checkin-reminder-settings", ctx["token"])
        payload = build_reminder_rules_variant()
        with self.put(url, headers=headers, json=payload, name="更新提醒规则", catch_response=True) as res:
            validate_api_response(res, "更新提醒规则", expect_data=True)


class Transaction_Jiankangfanganshengcheng(SerialTransaction):
    """健康方案: 历史列表 + 最新方案（generate 为 SSE 流，单独压测成本高）"""

    @property
    def transaction(self):
        return "jiankangfanganshengcheng"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/plan/history"
        headers = build_auth_headers("/living-plans", ctx["token"])
        params = {"page": 1, "size": 10}
        with self.get(url, headers=headers, params=params, name="方案历史", catch_response=True) as res:
            validate_api_response(res, "方案历史", expect_data=True)

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/plan/latest"
        headers = build_auth_headers("/living-plans", ctx["token"])
        with self.get(url, headers=headers, name="最新方案", catch_response=True) as res:
            # 无方案时后端返回 404，压测中视为可接受
            if res.status_code == 200:
                validate_api_response(res, "最新方案", expect_data=True)
            elif res.status_code == 404:
                validate_http_ok(res, "最新方案-无记录", accept_status=(404,))
            else:
                validate_api_response(res, "最新方案")


class Transaction_Kepuaizhushou(SerialTransaction):
    """科普助手: 首页内容"""

    @property
    def transaction(self):
        return "kepuAIzhushou"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v2/home/content"
        headers = build_auth_headers("/home", ctx["token"])
        with self.get(url, headers=headers, name="科普内容", catch_response=True) as res:
            validate_api_response(res, "科普内容", expect_data=True)


class Transaction_Zixuntuijian(SerialTransaction):
    """资讯推荐: 消息 + 文章列表 + 收藏"""

    @property
    def transaction(self):
        return "zixuntuijian"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/user/messages"
        headers = build_auth_headers("/health-info", ctx["token"])
        with self.get(url, headers=headers, name="用户消息", catch_response=True) as res:
            validate_api_response(res, "用户消息", expect_data=True)

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        url = f"{BASE_HOST}/api/v1/articles"
        headers = build_auth_headers("/health-info", ctx["token"])
        params = {"page": 1, "size": 10}
        with self.get(url, headers=headers, params=params, name="文章列表", catch_response=True) as res:
            if not validate_api_response(res, "文章列表", expect_data=True):
                return
            data = res.json().get("data") or {}
            items = data.get("articles") or data.get("list") or data.get("items") or []
            if items:
                article_id = items[0].get("articleId") or items[0].get("article_id") or items[0].get("id")
                if article_id:
                    ctx["last_article_id"] = article_id
                    resolve_root_user(self.parent)._auth_ctx = ctx

    @task
    def task_2(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        article_id = ctx.get("last_article_id")
        if not article_id:
            logger.debug("文章收藏跳过: 无可用 article_id")
            return

        url = f"{BASE_HOST}/api/v1/articles/{article_id}/favorite"
        headers = build_auth_headers(f"/health-info/{article_id}", ctx["token"])
        with self.post(url, headers=headers, json={}, name="收藏文章", catch_response=True) as res:
            validate_api_response(res, "收藏文章")


class Transaction_Aiyishengzixun(SerialTransaction):
    """AI 医生咨询: 创建会话 + 发消息 + AI 建议"""

    @property
    def transaction(self):
        return "AIyishengzixun"

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            return

        if ctx.get("consult_session_id"):
            return

        url = f"{BASE_HOST}/api/v2/consultations"
        headers = build_auth_headers("/consultation", ctx["token"])
        data = {"doctor_id": AI_DOCTOR_ID}
        with self.post(url, headers=headers, json=data, name="创建咨询会话", catch_response=True) as res:
            if not validate_api_response(res, "创建咨询会话", expect_data=True):
                return
            payload = res.json().get("data") or {}
            session_id = payload.get("sessionId") or payload.get("session_id")
            if session_id:
                ctx["consult_session_id"] = session_id
                resolve_root_user(self.parent)._auth_ctx = ctx

    @task
    def task_1(self):
        ctx = get_auth_ctx(self.parent)
        session_id = ctx.get("consult_session_id") if ctx else None
        if not ctx or not session_id:
            logger.debug("咨询消息跳过: 无 session_id")
            return

        url = f"{BASE_HOST}/api/v2/consultations/{session_id}/messages"
        headers = build_auth_headers(
            f"/consultation/chat?doctor_id={AI_DOCTOR_ID}&session_id={session_id}",
            ctx["token"],
        )
        data = {"content": "你好，我想问一下如何控制饮食，我是糖尿病前期"}
        with self.post(
            url,
            headers=headers,
            json=data,
            name="发送咨询消息",
            catch_response=True,
            timeout=180,
        ) as res:
            validate_api_response(res, "发送咨询消息", expect_data=True)

    @task
    def task_2(self):
        ctx = get_auth_ctx(self.parent)
        session_id = ctx.get("consult_session_id") if ctx else None
        if not ctx or not session_id:
            return

        url = f"{BASE_HOST}/api/v2/consultations/{session_id}/ai-suggestion"
        headers = build_auth_headers(
            f"/consultation/chat?doctor_id={AI_DOCTOR_ID}&session_id={session_id}",
            ctx["token"],
        )
        with self.get(
            url,
            headers=headers,
            name="咨询AI建议",
            catch_response=True,
            timeout=180,
        ) as res:
            validate_api_response(res, "咨询AI建议", expect_data=True)


class Transaction_Shipinbofang(SerialTransaction):
    """科普视频播放: Range 请求"""

    @property
    def transaction(self):
        return "shipinbofang"

    @task
    def task_0(self):
        url = f"{BASE_HOST}/api/v2/home/media/video/video_001.mp4"
        headers = {
            "Accept": "*/*",
            "Accept-Language": "zh-CN,zh;q=0.9",
            "Range": "bytes=0-",
            "Referer": f"{BASE_HOST}/home",
            "User-Agent": USER_AGENT,
        }
        with self.get(url, headers=headers, name="视频播放", catch_response=True) as res:
            validate_http_ok(res, "视频播放", accept_status=(200, 206))


class WebrunnerAction(BrowserAction):
    """
    事务集合。混合比默认均为 1；AI 类接口（咨询、打卡分析）可按需调低权重。
    """

    @task(1)
    @transaction("morenfenzu")
    def task_morenfenzu(self):
        Transaction_Morenfenzu(self).run()

    @task(1)
    @transaction("jinrushouye")
    def task_jinrushouye(self):
        Transaction_Jinrushouye(self).run()

    @task(1)
    @transaction("zhuce")
    def task_zhuce(self):
        Transaction_Zhuce(self).run()

    @task(1)
    @transaction("wangjimima")
    def task_wangjimima(self):
        Transaction_Wangjimima(self).run()

    @task(1)
    @transaction("denglu")
    def task_denglu(self):
        Transaction_Denglu(self).run()

    @task(1)
    @transaction("gerenxinxixiugai")
    def task_gerenxinxixiugai(self):
        Transaction_Gerenxinxixiugai(self).run()

    @task(1)
    @transaction("tangniaobingfengxianyuce")
    def task_tangniaobingfengxianyuce(self):
        Transaction_Tangniaobingfengxianyuce(self).run()

    @task(1)
    @transaction("shenghuodaka")
    def task_shenghuodaka(self):
        Transaction_Shenghuodaka(self).run()

    @task(1)
    @transaction("zidingyishiwushangchuan")
    def task_zidingyishiwushangchuan(self):
        Transaction_Zidingyishiwushangchuan(self).run()

    @task(1)
    @transaction("dakaxingweiAIfenxi")
    def task_dakaxingweiAIfenxi(self):
        Transaction_Dakaxingweiaifenxi(self).run()

    @task(1)
    @transaction("dakatixing")
    def task_dakatixing(self):
        Transaction_Dakatixing(self).run()

    @task(1)
    @transaction("jiankangfanganshengcheng")
    def task_jiankangfanganshengcheng(self):
        Transaction_Jiankangfanganshengcheng(self).run()

    @task(1)
    @transaction("kepuAIzhushou")
    def task_kepuAIzhushou(self):
        Transaction_Kepuaizhushou(self).run()

    @task(1)
    @transaction("zixuntuijian")
    def task_zixuntuijian(self):
        Transaction_Zixuntuijian(self).run()

    @task(1)
    @transaction("AIyishengzixun")
    def task_AIyishengzixun(self):
        Transaction_Aiyishengzixun(self).run()

    @task(1)
    @transaction("shipinbofang")
    def task_shipinbofang(self):
        Transaction_Shipinbofang(self).run()


class WebrunnerUser(CFastHttpUser):
    """
    虚拟用户: on_start 预热注册并登录，再循环执行 Action。
    """

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

    def _warmup_bind_email(self):
        """登录后绑定唯一邮箱，供忘记密码（邮箱 reset）压测使用。"""
        if not self._auth_ctx or not self._auth_ctx.get("token"):
            return

        email = build_user_email(self)
        self._auth_ctx["email"] = email
        self._auth_ctx["email_bound"] = False

        send_url = f"{BASE_HOST}/api/v1/auth/send-code"
        send_headers = build_headers("/forgot-password")
        send_data = {"account": email, "type": "email", "purpose": "bind"}
        with self.client.post(
            send_url,
            headers=send_headers,
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

        bind_url = f"{BASE_HOST}/api/v1/user/account/email"
        bind_headers = build_auth_headers("/user-center", self._auth_ctx["token"])
        bind_data = {"email": email, "verify_code": DEV_VERIFY_CODE}
        with self.client.put(
            bind_url,
            headers=bind_headers,
            json=bind_data,
            name="预热绑定邮箱",
            catch_response=True,
        ) as res:
            if validate_api_response(res, "预热绑定邮箱"):
                self._auth_ctx["email_bound"] = True
            else:
                logger.error(f"预热绑定邮箱失败: email={email}")
