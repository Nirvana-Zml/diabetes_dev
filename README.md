# 糖尿病智能助手

面向糖尿病患者及关注人群的 **AI 驱动健康管理平台**，提供科普教育、风险评估、AI 问诊、个性化健康方案、日常打卡、行为分析、资讯推荐与主动健康干预等能力。

> **免责声明：** 平台所有 AI 生成内容仅供参考，不能替代专业医生诊断与治疗建议。

---

## 功能概览


| 模块    | 能力                                    |
| ----- | ------------------------------------- |
| 科普与资讯 | 首页 Banner、科普视频、健康资讯、个性化推荐、AI 听全文（TTS） |
| AI 助手 | 小糖助手科普问答（SSE 流式）、Milvus 知识检索、语音输入     |
| 健康管理  | 健康档案、糖尿病风险评估、AI 健康趋势分析、个性化健康方案        |
| 生活打卡  | 食物 / 用药 / 运动 / 血糖打卡、AI 图片识别、行为分析、提醒   |
| 在线问诊  | AI 模拟医生图文对话、会话历史                      |
| 管理后台  | 资讯/视频管理、AI 初稿生成、运营统计、审计日志             |
| 主动干预  | 基于健康趋势的规则 + AI 预警消息推送                 |


**业务闭环：**

```text
科普教育 → 风险评估 → AI 问诊 → 健康方案 → 日常打卡 → 行为分析 → 资讯推荐 → 主动干预
```

---

## 技术架构

```text
frontend / admin-frontend (Vue 3 + Vite)
        ↓
   Nginx (:80 / :81)
        ↓
Spring Cloud Gateway (:8099)
        ↓
8 个微服务 (Spring Boot 3 + Java 17)
        ↓
MySQL · Redis · MinIO · Milvus · KingbaseES
        ↓
Dify · Ollama · 阿里云 DashScope（宿主机 / 外部 API）
```


| 层级  | 技术                                                      |
| --- | ------------------------------------------------------- |
| 前端  | Vue 3.5、Vite 6、Element Plus、ECharts、Axios               |
| 后端  | Spring Boot 3.3、Spring Cloud Gateway、MyBatis            |
| 数据  | MySQL 8.0（分库）、Redis 7.2、MinIO、Milvus 2.4、KingbaseES（审计） |
| AI  | Dify 工作流、Milvus RAG、DashScope STT/TTS、Ollama Embedding  |
| 部署  | Docker Compose + Nginx                                  |


---



## 快速开始



### 环境要求

Docker 24+、Docker Compose 2.20+；本地开发另需 Node.js 20、Java 17、Maven 3.9+。

### 一键部署

```bash
git clone https://gitee.com/nirvana-zml/diabetes_dev.git
cd diabetes_dev
cp .env.example .env
# 编辑 .env：数据库密码、JWT_SECRET、DIFY_*_API_KEY 等

# 宿主机先启动 Dify（默认 http://localhost:58080）并配置各工作流

docker compose up -d
```



### 访问地址


| 入口     | Docker（Nginx）                                                | 本地 Vite Dev                                    |
| ------ | ------------------------------------------------------------ | ---------------------------------------------- |
| 用户端    | [http://localhost/](http://localhost/)                       | [http://localhost:5173](http://localhost:5173) |
| 管理端    | [http://localhost:81/](http://localhost:81/)                 | [http://localhost:5174](http://localhost:5174) |
| API 网关 | [http://localhost:8099/api/v1](http://localhost:8099/api/v1) | 同左                                             |




### 测试账号

执行 `db/init.sql` 后预置账号，**密码均为** `123456`：


| 类型   | 用户名                              | 说明                                               |
| ---- | -------------------------------- | ------------------------------------------------ |
| 普通用户 | `testuser` / `zhangsan` / `lisi` | 用户端登录                                            |
| 管理员  | `admin`                          | 管理端 [http://localhost:81/](http://localhost:81/) |


---



## 项目结构

```text
diabetes_dev/
├── frontend/              # 用户端（Vue 3）
├── admin-frontend/        # 管理端（Vue 3）
├── backend/               # 后端微服务
│   ├── gateway/           # API 网关 :8099
│   ├── common/            # 公共模块（DifyClient、MinIO 等）
│   ├── user-service/      # 用户、认证、消息、干预 :8081
│   ├── home-service/      # 首页、科普问答、视频 :8082
│   ├── health-service/    # 健康档案、风险评估 :8083
│   ├── checkin-service/   # 打卡、行为分析、食物识别 :8084
│   ├── consultation-service/  # AI 问诊 :8085
│   ├── plan-service/      # 健康方案 :8086
│   ├── article-service/   # 资讯、推荐、TTS :8087
│   └── audit-service/     # 审计日志 :8088
├── db/                    # init.sql、审计库脚本
├── docker/                # Nginx 配置
├── docs/                  # 设计文档、Dify 契约等
├── docker-compose.yml
├── .env.example
├── README.md              # 本文件
├── 开发指南.md
└── 使用说明.md
```

---



## 文档索引


| 文档                                                   | 说明                  |
| ---------------------------------------------------- | ------------------- |
| [使用说明.md](./使用说明.md)                                 | 用户端与管理端操作指南、常见问题    |
| [开发指南.md](./开发指南.md)                                 | 环境配置、本地开发、测试、AI 集成  |
| [docs/Dify工作流数据契约.md](./docs/Dify工作流数据契约.md)         | 全部 Dify 工作流入参/出参    |
| [docs/项目使用手册.md](./docs/项目使用手册.md)                   | 完整技术说明（与 README 互补） |
| [docs/Milvus医学知识库落地指南.md](./docs/Milvus医学知识库落地指南.md) | 科普 RAG 知识库导入        |


---



## 许可证

本项目为课程用途，具体许可证以仓库声明为准。