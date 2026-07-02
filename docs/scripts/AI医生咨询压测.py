# -*- coding: utf-8 -*-
"""
AI 医生咨询 — Dify 问诊专项压测脚本（WebRunner）

【导入说明】请整文件替换 WebRunner 编辑器内容，勿在末尾追加粘贴。

覆盖接口（与 /consultation/chat 发送消息、AI 建议面板一致）:
  POST /api/v2/consultations                         创建咨询会话（首访）
  GET  /api/v2/consultations/{id}/messages           加载历史消息（可选）
  POST /api/v2/consultations/{id}/messages           发送用户消息 + Dify 阻塞式 AI 回复（核心）
  GET  /api/v2/consultations/{id}/ai-suggestion      拉取 AI 结构化建议

相对浏览器录制脚本的改进:
  - 去除硬编码 JWT / session_id，虚拟用户预热：注册 → 登录
  - 运行时创建或复用 consult_session_id，问题池轮换
  - catch_response + validate_api_response + wr.checkpoint
  - 单 @task 串行流，避免中间 task 跳过导致 WebRunner 事务异常
  - Dify 阻塞调用默认 180s 超时，低并发场景预设

环境变量（均可选）:
  WR_BASE_HOST              默认 http://192.168.22.28
  WR_DEFAULT_PASSWORD       默认 Test123456
  WR_AI_DOCTOR_ID           默认 doc_002
  WR_MESSAGE_TIMEOUT        发消息超时秒数，默认 180（须为 int，与前端一致）
  WR_NETWORK_TIMEOUT        HTTP 客户端超时，默认 max(190, timeout+10)
  WR_NEW_SESSION_EACH_ROUND true 时每轮新建会话（默认 false，同用户复用会话）
  WR_INCLUDE_PAGE_LOAD      true 时首访追加 departments + ai-doctors + messages
  WR_SKIP_AI_SUGGESTION     true 时跳过 ai-suggestion（仅压发消息）
  WR_MULTI_TURN             true 时同轮连发 2 条消息（测多轮上下文）
  WR_STRICT_AI              true 时要求 ai_message 非空且非降级文案
  WR_ROUND_SLEEP            每轮事务结束休眠秒数，默认 3
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
AI_DOCTOR_ID = os.getenv("WR_AI_DOCTOR_ID", "doc_002")
MESSAGE_TIMEOUT = int(os.getenv("WR_MESSAGE_TIMEOUT", "180"))
NETWORK_TIMEOUT = int(
    os.getenv("WR_NETWORK_TIMEOUT", str(max(MESSAGE_TIMEOUT + 10, 190)))
)
NEW_SESSION_EACH_ROUND = (
    os.getenv("WR_NEW_SESSION_EACH_ROUND", "false").lower() == "true"
)
INCLUDE_PAGE_LOAD = os.getenv("WR_INCLUDE_PAGE_LOAD", "false").lower() == "true"
SKIP_AI_SUGGESTION = os.getenv("WR_SKIP_AI_SUGGESTION", "false").lower() == "true"
MULTI_TURN = os.getenv("WR_MULTI_TURN", "false").lower() == "true"
STRICT_AI = os.getenv("WR_STRICT_AI", "false").lower() == "true"
ROUND_SLEEP = float(os.getenv("WR_ROUND_SLEEP", "3"))

REFERER_LIST = "/consultation"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/114.0.0.0 Safari/537.36"
)

AI_UNAVAILABLE_MARKERS = (
    "AI 医生暂不可用",
    "请稍后再试",
)

CONSULT_QUESTIONS = [
    "你好，我想问一下如何控制饮食？",
    "最近空腹血糖偏高，需要调整用药吗？",
    "糖尿病前期该如何饮食控制？",
    "运动后血糖反而升高，正常吗？",
    "二甲双胍有哪些常见副作用？",
]

FOLLOWUP_QUESTIONS = [
    "那日常具体应该怎么安排三餐？",
    "如果还是偏高，多久需要复查？",
    "有没有需要特别避免的食物？",
    "这种情况需要立刻就医吗？",
]

_seq_lock = threading.Lock()
_seq_counter = itertools.count(1)


def _next_seq() -> int:
    with _seq_lock:
        return next(_seq_counter) + int(time.time() * 1000) % 1_000_000


def resolve_root_user(node):
    while hasattr(node, "parent") and node.parent is not None:
        node = node.parent
    return node


def chat_referer(session_id: str, doctor_id: str = AI_DOCTOR_ID) -> str:
    return f"/consultation/chat?doctor_id={doctor_id}&session_id={session_id}"


def build_headers(referer_path: str = REFERER_LIST) -> dict:
    return {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Content-Type": "application/json",
        "Origin": BASE_HOST,
        "Referer": f"{BASE_HOST}{referer_path}",
        "User-Agent": USER_AGENT,
    }


def build_auth_headers(token: str, referer_path: str = REFERER_LIST) -> dict:
    headers = build_headers(referer_path)
    headers["Authorization"] = f"Bearer {token}"
    return headers


def build_register_payload(user) -> dict:
    seq = _next_seq()
    user_id = getattr(user, "id", id(user)) % 100_000
    username = f"consult_{user_id}_{seq}"
    phone_suffix = f"{seq % 100_000_000:08d}"
    phone = f"152{phone_suffix}"
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


def bump_round(ctx: Optional[Dict]) -> int:
    if ctx is None:
        return 1
    ctx["round"] = int(ctx.get("round", 0)) + 1
    return ctx["round"]


def pick_question(user, round_no: int, *, followup: bool = False) -> str:
    pool = FOLLOWUP_QUESTIONS if followup else CONSULT_QUESTIONS
    user_id = getattr(user, "id", id(user)) if user else 0
    idx = (user_id + round_no) % len(pool)
    return pool[idx]


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


def _extract_session_id(payload: dict) -> Optional[str]:
    if not payload:
        return None
    return payload.get("sessionId") or payload.get("session_id")


def _extract_ai_message(payload: dict) -> Optional[dict]:
    if not payload:
        return None
    ai = payload.get("aiMessage") or payload.get("ai_message")
    if isinstance(ai, dict):
        return ai
    return None


def _ai_content_ok(content: Optional[str]) -> bool:
    if not content or not str(content).strip():
        return False
    text = str(content)
    return not any(marker in text for marker in AI_UNAVAILABLE_MARKERS)


def validate_send_message_response(res, checkpoint_prefix: str) -> bool:
    if not validate_api_response(res, checkpoint_prefix, expect_data=True):
        return False

    payload = res.json().get("data") or {}
    ai_msg = _extract_ai_message(payload)
    content = (ai_msg or {}).get("content")

    if STRICT_AI and not _ai_content_ok(content):
        msg = f"{checkpoint_prefix} AI 回复无效或降级: {content!r}"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint(f"{checkpoint_prefix}-含AI回复", ai_msg is not None)
        if content:
            wr.checkpoint(f"{checkpoint_prefix}-AI内容非空", True)
    except Exception:
        pass

    logger.info(
        f"{checkpoint_prefix} 完成: ai_len={len(str(content or ''))}, "
        f"message_id={payload.get('messageId') or payload.get('message_id')}"
    )
    return True


def validate_ai_suggestion_response(res, checkpoint_prefix: str) -> bool:
    if not validate_api_response(res, checkpoint_prefix, expect_data=True):
        return False

    payload = res.json().get("data") or {}
    has_content = any(
        [
            payload.get("possibleDiagnoses") or payload.get("possible_diagnoses"),
            payload.get("recommendedExams") or payload.get("recommended_exams"),
            payload.get("suggestedQuestions") or payload.get("suggested_questions"),
            payload.get("treatmentStrategy") or payload.get("treatment_strategy"),
        ]
    )

    if STRICT_AI and not has_content:
        msg = f"{checkpoint_prefix} 建议面板为空（WR_STRICT_AI=true）"
        logger.warning(msg)
        if hasattr(res, "failure"):
            res.failure(msg)
        return False

    try:
        wr.checkpoint(f"{checkpoint_prefix}-建议结构", True)
    except Exception:
        pass
    return True


class _ConsultChatMixin:
    parent: "User"

    def _auth_headers(self, referer_path: str = REFERER_LIST) -> dict:
        ctx = get_auth_ctx(self.parent)
        token = (ctx or {}).get("token")
        if not token:
            return build_headers(referer_path)
        return build_auth_headers(token, referer_path)

    def _ensure_session(self, ctx: Dict, *, force_new: bool = False) -> Optional[str]:
        if not force_new and ctx.get("consult_session_id"):
            return ctx["consult_session_id"]

        url = f"{BASE_HOST}/api/v2/consultations"
        headers = self._auth_headers(REFERER_LIST)
        data = {"doctor_id": AI_DOCTOR_ID}

        with self.post(
            url,
            headers=headers,
            json=data,
            name="创建咨询会话",
            catch_response=True,
        ) as res:
            if not validate_api_response(res, "创建咨询会话", expect_data=True):
                return None
            session_id = _extract_session_id(res.json().get("data") or {})
            if not session_id:
                msg = "创建咨询会话成功但 session_id 为空"
                logger.warning(msg)
                if hasattr(res, "failure"):
                    res.failure(msg)
                return None

        ctx["consult_session_id"] = session_id
        resolve_root_user(self.parent)._auth_ctx = ctx
        return session_id

    def _optional_page_load(self, session_id: str) -> bool:
        if not INCLUDE_PAGE_LOAD:
            return True

        ok = True
        url_dept = f"{BASE_HOST}/api/v2/ai-doctors/departments"
        with self.get(
            url_dept,
            headers=self._auth_headers(REFERER_LIST),
            name="咨询科室列表",
            catch_response=True,
        ) as res:
            ok = validate_api_response(res, "咨询科室列表") and ok

        url_docs = f"{BASE_HOST}/api/v2/ai-doctors"
        with self.get(
            url_docs,
            headers=self._auth_headers(REFERER_LIST),
            params={"department": "", "status": "", "keyword": ""},
            name="AI医生列表",
            catch_response=True,
        ) as res:
            ok = validate_api_response(res, "AI医生列表") and ok

        url_msgs = f"{BASE_HOST}/api/v2/consultations/{session_id}/messages"
        with self.get(
            url_msgs,
            headers=self._auth_headers(chat_referer(session_id)),
            params={"page": 1, "size": 50},
            name="咨询历史消息",
            catch_response=True,
        ) as res:
            ok = validate_api_response(res, "咨询历史消息", expect_data=True) and ok

        return ok

    def _send_message(self, session_id: str, content: str, *, name: str) -> bool:
        url = f"{BASE_HOST}/api/v2/consultations/{session_id}/messages"
        headers = self._auth_headers(chat_referer(session_id))
        started = time.time()

        try:
            with self.post(
                url,
                headers=headers,
                json={"content": content},
                name=name,
                catch_response=True,
                timeout=MESSAGE_TIMEOUT,
            ) as res:
                elapsed = time.time() - started
                logger.info(
                    f"{name} 耗时: {elapsed:.2f}s (timeout={MESSAGE_TIMEOUT}s)"
                )
                return validate_send_message_response(res, name)
        except Exception as exc:
            elapsed = time.time() - started
            msg = (
                f"{name} 异常（elapsed={elapsed:.2f}s, "
                f"timeout={MESSAGE_TIMEOUT}s, client={NETWORK_TIMEOUT}s）: {exc}"
            )
            logger.error(msg)
            try:
                wr.checkpoint(f"{name}-请求成功", False)
            except Exception:
                pass
            return False

    def _fetch_ai_suggestion(self, session_id: str) -> bool:
        url = f"{BASE_HOST}/api/v2/consultations/{session_id}/ai-suggestion"
        headers = self._auth_headers(chat_referer(session_id))
        started = time.time()

        try:
            with self.get(
                url,
                headers=headers,
                name="咨询AI建议",
                catch_response=True,
                timeout=MESSAGE_TIMEOUT,
            ) as res:
                elapsed = time.time() - started
                logger.info(f"咨询AI建议 耗时: {elapsed:.2f}s")
                return validate_ai_suggestion_response(res, "咨询AI建议")
        except Exception as exc:
            msg = f"咨询AI建议 异常: {exc}"
            logger.error(msg)
            try:
                wr.checkpoint("咨询AI建议-请求成功", False)
            except Exception:
                pass
            return False


class 测试负载(WebLoadMachine):
    """用户自定义负载信息"""

    负载名称 = "AI医生咨询压测"

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
    Dify 问诊专项场景（低并发）。

    建议: 用户数 5~8、创建速率 1、运行时长 600s。
    """

    场景名称 = "AI医生咨询-梯形负载"

    模式 = "梯形负载"
    参数 = {
        "用户数": 8,
        "创建速率": 1,
        "运行时长": 600,
    }


class Transaction_Aiyishengzixun(_ConsultChatMixin, SerialTransaction):
    """
    AI 医生咨询（单 @task）:
      确保会话 → 可选页面预加载 → 发消息(Dify) → AI 建议
      WR_MULTI_TURN=true 时同轮追问一条
    """

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    @property
    def transaction(self):
        return "AIyishengzixun"

    def on_start(self):
        root = resolve_root_user(self.parent)
        ctx = getattr(root, "_auth_ctx", None)
        round_no = bump_round(ctx)
        self._round_no = round_no
        super().on_start()

    @task
    def task_0(self):
        ctx = get_auth_ctx(self.parent)
        if not ctx:
            logger.warning("AI医生咨询跳过: 未登录")
            return

        root = resolve_root_user(self.parent)
        force_new = NEW_SESSION_EACH_ROUND
        session_id = self._ensure_session(ctx, force_new=force_new)
        if not session_id:
            logger.warning("AI医生咨询跳过: 无法创建会话")
            return

        if not self._optional_page_load(session_id):
            logger.warning("AI医生咨询: 页面预加载部分失败，继续压核心接口")

        question = pick_question(root, self._round_no, followup=False)
        if not self._send_message(session_id, question, name="发送咨询消息"):
            return

        if not SKIP_AI_SUGGESTION:
            self._fetch_ai_suggestion(session_id)

        if not MULTI_TURN:
            return

        followup = pick_question(root, self._round_no, followup=True)
        if not self._send_message(session_id, followup, name="发送咨询消息-追问"):
            return

        if not SKIP_AI_SUGGESTION:
            self._fetch_ai_suggestion(session_id)

    def on_stop(self):
        if ROUND_SLEEP > 0:
            time.sleep(ROUND_SLEEP)
        super().on_stop()


class WebrunnerAction(BrowserAction):
    """事务集合: AI 医生咨询。"""

    def __init__(self, parent: "User") -> None:
        super().__init__(parent)

    def on_start(self):
        super().on_start()

    @task(1)
    @transaction("AIyishengzixun")
    def task_AIyishengzixun(self):
        Transaction_Aiyishengzixun(self).run()

    def on_stop(self):
        super().on_stop()


class WebrunnerUser(CFastHttpUser):
    """虚拟用户: 预热鉴权后循环发起 AI 医生咨询。"""

    host = BASE_HOST
    tasks = [WebrunnerAction]
    network_timeout = NETWORK_TIMEOUT
    connection_timeout = NETWORK_TIMEOUT

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._auth_ctx = {"round": 0}

    def on_start(self):
        super().on_start()
        self._apply_client_timeout()
        self._warmup_register()
        self._warmup_login()
        logger.info(
            f"AI医生咨询虚拟用户启动: id={getattr(self, 'id', id(self))}, "
            f"doctor={AI_DOCTOR_ID}, msg_timeout={MESSAGE_TIMEOUT}s, "
            f"multi_turn={MULTI_TURN}, new_session={NEW_SESSION_EACH_ROUND}"
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
                self._auth_ctx.update(
                    {
                        "username": data["username"],
                        "phone": data["phone"],
                        "password": data["password"],
                        "token": None,
                    }
                )
            else:
                logger.error(f"预热注册失败: username={data['username']}")

    def _warmup_login(self):
        if not self._auth_ctx.get("username"):
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
