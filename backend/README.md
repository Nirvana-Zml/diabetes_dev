# 糖尿病智能助手 — 后端微服务

Spring Boot 3 + MyBatis + Java 17，7 个业务微服务 + 1 个 API 网关，对应 `db/init.sql` 中各分库。

## 服务清单（对齐设计文档八大模块）

| 服务 | 端口 | 数据库 | 设计模块 | 主要路径 |
|------|------|--------|----------|----------|
| gateway | 8099 | — | — | 统一入口 `/api/v1/**` |
| user-service | 8081 | DIABETES_USER | 个人中心管理 | `/auth/*`, `/user/*`（**不改动**） |
| home-service | 8082 | — | 科普展示首页 | `/home/*`, `/chat/*`（**占位，未实现**） |
| health-service | 8083 | DIABETES_HEALTH | 糖尿病风险预测 | `/risk/*` |
| checkin-service | 8084 | DIABETES_CHECKIN | 生活打卡 + 打卡信息管理 | `/checkin/*`, `/checkin-management/*` |
| consultation-service | 8085 | — | 医生在线咨询 | `/ai-doctors/*`, `/consultations/*`（**占位，未实现**） |
| plan-service | 8086 | DIABETES_PLAN | 健康方案生成 | `/plan/*` |
| article-service | 8087 | DIABETES_ARTICLE | 健康资讯管理 | `/articles/*`, `/admin/articles/*` |

## 架构要点

- **业务逻辑**：除 AI 能力外均由 Spring Boot + MyBatis 实现（用户 CRUD、打卡、风险评估计算器、方案结构化存储等）。
- **AI 能力**：风险预测、方案生成、打卡行为分析等通过 `common` 模块的 `DifyClient` 调用 Dify 工作流；未配置 API Key 时自动降级为本地规则/模板结果。
- **占位模块**：`home-service`（科普首页）、`consultation-service`（医生咨询）对外返回 501 或空数据；其他服务通过 `HomeServiceClient` / `ConsultationServiceClient` 内部占位调用，不阻断主流程。
- **Redis**：`checkin-service` 缓存今日打卡与统计，`article-service` 缓存推荐/列表；Key 前缀 `diabetes:`，可与 Dify 共用同一 Redis 实例。

## 环境变量

| 变量 | 说明 |
|------|------|
| `JWT_SECRET` | JWT 签名密钥 |
| `DIFY_INTERNAL_KEY` | Dify HTTP 工具访问内部接口 |
| `DIFY_BASE_URL` | Dify 服务地址 |
| `DIFY_RISK_API_KEY` | 风险评估工作流 API Key |
| `DIFY_PLAN_API_KEY` | 方案生成工作流 API Key |
| `DIFY_CHECKIN_API_KEY` | 打卡分析工作流 API Key |
| `DIFY_ARTICLE_DRAFT_API_KEY` | 资讯 AI 初稿（前端直调，后端仅提供配置） |
| `REDIS_HOST` | Redis 地址（Docker 内为 `redis`，本地默认 `localhost`） |
| `REDIS_PORT` | Redis 端口（Docker 内 `6379`，宿主机映射 `6380`） |
| `REDIS_PASSWORD` | Redis 密码 |

## 本地构建

```bash
cd backend
mvn clean package -DskipTests
```

## Docker 启动

Docker 构建使用 `backend/maven-settings.xml`（阿里云镜像），避免访问 Maven Central 时 SSL 握手失败。若仍报错 `Remote host terminated the handshake`，多为瞬时网络问题，重试：

```bash
docker compose build --no-cache checkin-service
```

```bash
docker compose up -d mysql redis
docker compose up -d --build gateway user-service home-service health-service checkin-service consultation-service plan-service article-service
```

本地直连 Redis（非 Docker 网络）：`REDIS_HOST=localhost REDIS_PORT=6380`

## 核心 API 示例

### 风险预测（需 JWT）

- `POST /api/v1/risk/assess` — 提交评估（本地 MedicalCalculator + Dify 工作流）
- `GET /api/v1/risk/history` — 评估历史
- `GET /api/v1/risk/{assessmentId}` — 评估详情

### 健康方案（需 JWT）

- `POST /api/v1/plan/generate` — SSE 流式生成方案
- `GET /api/v1/plan/latest` — 最新方案

### 生活打卡（需 JWT）

- `POST /api/v1/checkin` — 创建打卡
- `GET /api/v1/checkin-management/ai-summary` — AI 行为分析（Dify）

### 占位模块

- `GET /api/v1/home/content` → 501 科普首页尚未实现
- `POST /api/v1/consultations` → 501 医生咨询尚未实现
