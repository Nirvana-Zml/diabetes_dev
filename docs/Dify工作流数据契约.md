# Dify 工作流数据契约

本文档汇总本项目所有**后端调用**的 Dify 工作流：传入数据结构、期望返回数据结构、环境变量与代码/Schema 文件位置。

---

## 1. 通用约定

### 1.1 调用方式

| 项目 | 说明 |
|------|------|
| 基础 URL | `DIFY_BASE_URL`（Docker 内推荐 `http://host.docker.internal:58080`） |
| 接口 | `POST {DIFY_BASE_URL}/v1/workflows/run` |
| 认证 | `Authorization: Bearer {API_KEY}` |
| 响应模式 | 默认 `blocking`（阻塞，一次返回完整结果） |

### 1.2 HTTP 请求体结构

```json
{
  "response_mode": "blocking",
  "user": "usr_xxx",
  "inputs": {
    "inputs": { ...业务 payload，见各工作流章节... }
  }
}
```

- 工作流「开始」节点变量名统一为 **`inputs`**（JSON Object 类型）时，请求体为 `{ "inputs": { "inputs": { ... } } }`。
- **健康方案生成**例外：开始节点为 7 个独立变量，请求体为 `{ "inputs": { query, user_id, daily_calories, ... } }`（见第 4 章）。
- 环境变量 `DIFY_*_INPUT_VAR=inputs`，`DIFY_*_INPUT_FORMAT=object`（健康方案工作流不使用这两项）。
- 当 `input-format=string` 时，内层 `inputs` 值为 JSON 字符串（兼容部分 Dify 版本）。

### 1.3 JSON Schema 统一格式

所有工作流「开始」节点 `inputs` 变量的 JSON Schema 均遵循以下结构（`foo`/`bar` 为示意，实际字段见各工作流章节）：

```json
{
  "type": "object",
  "properties": {
    "foo": {
      "type": "string"
    },
    "bar": {
      "type": "object",
      "properties": {
        "sub": {
          "type": "number"
        }
      },
      "required": [],
      "additionalProperties": true
    }
  },
  "required": [],
  "additionalProperties": true
}
```

约定说明：

- 顶层及所有嵌套 `object`：必须包含 `"required": []` 与 `"additionalProperties": true`
- 字符串字段：仅 `{ "type": "string" }`
- 数值字段：`{ "type": "number" }` 或 `{ "type": "integer" }`
- 布尔字段：`{ "type": "boolean" }`
- 数组字段：`{ "type": "array", "items": { ... } }`；若 `items` 为 `object`，同样含 `required: []` 与 `additionalProperties: true`

### 1.4 阻塞模式响应结构

```json
{
  "data": {
    "status": "succeeded",
    "outputs": {
      "<output_key>": { ...工作流输出... }
    }
  }
}
```

后端解析顺序：`data.outputs.<key>` → `outputs.<key>` → `outputs.text` 内嵌 JSON。

### 1.5 公共工具

| 路径 | 说明 |
|------|------|
| `backend/common/.../DifyClient.java` | Dify HTTP 客户端 |
| `backend/common/.../DifyJsonSchema.java` | 统一 Schema 构建与 payload 包装 |

---

## 2. 工作流一览

| # | 业务 | 服务 | 环境变量 | 输出变量名 | 响应模式 | 契约查询 API |
|---|------|------|----------|------------|----------|--------------|
| 1 | 打卡 AI 行为分析 | checkin-service | `DIFY_CHECKIN_API_KEY` | `behavior_analysis` | blocking | `GET /api/v1/checkin-management/dify-workflow-spec` |
| 2 | 健康方案生成 | plan-service | `DIFY_PLAN_API_KEY` | `plan_llm_output`（浅层，后端组装） | blocking | `GET /api/v1/plan/dify-workflow-spec` |
| 3 | 糖尿病风险评估 | health-service | `DIFY_RISK_API_KEY` | `risk_assessment` | blocking | `GET /api/v1/risk/dify-workflow-spec` |
| 4 | 资讯个性化推荐 | article-service | `DIFY_ARTICLE_RECOMMEND_API_KEY` | `article_info.recommendations` | blocking | `GET /api/v1/articles/recommend/dify-workflow-spec` |
| 5 | 健康趋势分析 | user-service | `DIFY_HEALTH_TREND_API_KEY` | `trend_analysis` | blocking | `GET /api/v1/user/health-trend/dify-workflow-spec` |
| 6 | AI 模拟医生问诊 | consultation-service | `DIFY_CONSULTATION_API_KEY` | `doctor_reply` | blocking | `GET /api/v1/consultations/dify-workflow-spec` |
| 7 | 科普问答 | home-service | `DIFY_QA_API_KEY` | `valid` / `text` 等 5 字段 | streaming | `GET /api/v1/chat/dify-workflow-spec` |
| 8 | 食物图片识别 | checkin-service | `DIFY_FOOD_RECOGNITION_API_KEY` | `food_recognition` | blocking | `GET /api/v1/checkin/food/dify-workflow-spec` |
| 9 | 资讯初稿生成 | 管理端直调 / article-service 下发 config | `DIFY_ARTICLE_DRAFT_API_KEY` | `article_draft` | streaming | `GET /api/v1/admin/articles/ai-draft/config` |

---

## 3. 打卡 AI 行为分析

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/checkin-service/.../DifyCheckinAnalysisWorkflowContract.java` |
| 入参 Schema 文件 | `backend/checkin-service/src/main/resources/dify/checkin-analysis-input.schema.json` |
| 默认 API Key | `app-kFnPhD8YUmCfG2DeHubp1Scc` |

### 3.1 传入数据 JSON Schema（`inputs`，7 字段平铺）

> 与 `backend/checkin-service/src/main/resources/dify/checkin-analysis-input.schema.json` 一致。  
> **开始节点配置 7 个独立变量**，后端/API 直接将各字段写入请求体 `inputs`。  
> 专家角色与输出约束写在 **LLM 节点系统提示词**中。

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string"
    },
    "user_id": {
      "type": "string"
    },
    "start_date": {
      "type": "string"
    },
    "end_date": {
      "type": "string"
    },
    "checkin_stats": {
      "type": "object",
      "properties": {
        "totalCheckins": {
          "type": "integer"
        },
        "completionRate": {
          "type": "number"
        },
        "totalPoints": {
          "type": "integer"
        },
        "streakDays": {
          "type": "integer"
        },
        "calendarData": {
          "type": "object",
          "properties": {},
          "required": [],
          "additionalProperties": true
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "trend_data": {
      "type": "object",
      "properties": {
        "dietTrend": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "date": {
                "type": "string"
              },
              "count": {
                "type": "integer"
              }
            },
            "required": [],
            "additionalProperties": true
          }
        },
        "exerciseTrend": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "date": {
                "type": "string"
              },
              "count": {
                "type": "integer"
              }
            },
            "required": [],
            "additionalProperties": true
          }
        },
        "medicationTrend": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "date": {
                "type": "string"
              },
              "count": {
                "type": "integer"
              }
            },
            "required": [],
            "additionalProperties": true
          }
        },
        "glucoseTrend": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "date": {
                "type": "string"
              },
              "count": {
                "type": "integer"
              }
            },
            "required": [],
            "additionalProperties": true
          }
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "user_profile": {
      "type": "object",
      "properties": {
        "height": {
          "type": "number"
        },
        "weight": {
          "type": "number"
        },
        "bmi": {
          "type": "number"
        },
        "fastingGlucose": {
          "type": "number"
        }
      },
      "required": [],
      "additionalProperties": true
    }
  },
  "required": [],
  "additionalProperties": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `query` | string | 固定分析请求文案 |
| `user_id` | string | 当前用户 ID |
| `start_date` / `end_date` | string | 分析日期范围 `YYYY-MM-DD` |
| `checkin_stats` | object | 打卡统计（次数、完成率、积分、连续天、日历） |
| `checkin_stats.totalCheckins` | integer | 区间内打卡总次数 |
| `checkin_stats.completionRate` | number | 完成率 0~1 |
| `checkin_stats.totalPoints` | integer | 累计积分 |
| `checkin_stats.streakDays` | integer | 连续打卡天数 |
| `checkin_stats.calendarData` | object | 日期 → 各类型是否打卡 |
| `trend_data` | object | 四类打卡每日计数趋势 |
| `trend_data.*Trend[]` | array | `{ date: string, count: integer }` |
| `user_profile` | object | 最新健康档案，无档案时为 `{}` |

**HTTP 请求体示例（平铺 7 字段）：**

```json
{
  "response_mode": "blocking",
  "user": "usr_001",
  "inputs": {
    "query": "请分析用户的打卡行为数据，生成行为分析总结和改善建议",
    "user_id": "usr_001",
    "start_date": "2024-06-01",
    "end_date": "2024-06-30",
    "checkin_stats": {
      "totalCheckins": 85,
      "completionRate": 0.81,
      "totalPoints": 1200,
      "streakDays": 7,
      "calendarData": {}
    },
    "trend_data": {
      "dietTrend": [{ "date": "2024-06-01", "count": 3 }],
      "exerciseTrend": [],
      "medicationTrend": [],
      "glucoseTrend": []
    },
    "user_profile": {
      "height": 170,
      "weight": 72,
      "bmi": 24.9,
      "fastingGlucose": 5.6
    }
  }
}
```

### 3.2 工作流需返回（`outputs.behavior_analysis`）

```json
{
  "summary": "本月您共完成85次打卡，完成率81%。饮食打卡最规律...",
  "behavior_patterns": [
    {
      "type": "diet",
      "pattern": "规律",
      "completion_rate": 0.95,
      "description": "饮食打卡完成率最高，三餐记录完整",
      "suggestion": "继续保持"
    }
  ],
  "anomalies": [
    {
      "date": "2024-06-05",
      "type": "missed_all",
      "value": 8.2,
      "description": "当日全部打卡缺失",
      "possible_reason": "可能外出或身体不适"
    }
  ],
  "improvements": [
    "建议固定每日打卡时间，形成习惯",
    "增加工作日运动安排"
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `summary` | string | 行为分析总结（Markdown） |
| `behavior_patterns[].type` | string | `diet` / `exercise` / `medication` / `glucose` |
| `behavior_patterns[].pattern` | string | `规律` / `不稳定` / `需加强` |
| `behavior_patterns[].completion_rate` | number | 完成率 0~1 |
| `behavior_patterns[].description` | string | 模式描述 |
| `behavior_patterns[].suggestion` | string | 改善建议 |
| `anomalies[].date` | string | 异常日期 |
| `anomalies[].type` | string | `missed_all` / `glucose_abnormal` / `medication_missed` |
| `anomalies[].value` | number | 可选，如异常血糖值 |
| `anomalies[].description` | string | 异常描述 |
| `anomalies[].possible_reason` | string | 可能原因 |
| `improvements[]` | string[] | 综合改善建议 |

---

## 4. 健康方案生成

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/plan-service/.../DifyPlanWorkflowContract.java` |
| 入参 Schema 文件 | `backend/plan-service/src/main/resources/dify/plan-generation-input.schema.json` |
| 出参 Schema 文件 | `backend/plan-service/src/main/resources/dify/plan-generation-llm-output.schema.json` |
| 组装类 | `backend/plan-service/.../DifyPlanLlmOutputAssembler.java` |
| 默认 API Key | `app-fHORDdQjVq9GVtLcyD9WaL3g` |

### 4.1 传入数据 JSON Schema（`inputs`，7 字段平铺）

> 与 `backend/plan-service/src/main/resources/dify/plan-generation-input.schema.json` 一致。  

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string"
    },
    "user_id": {
      "type": "string"
    },
    "daily_calories": {
      "type": "integer"
    },
    "user_profile": {
      "type": "object",
      "properties": {
        "age": {
          "type": "integer"
        },
        "gender": {
          "type": "string"
        },
        "nickname": {
          "type": "string"
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "health_profile": {
      "type": "object",
      "properties": {
        "height": {
          "type": "number"
        },
        "weight": {
          "type": "number"
        },
        "bmi": {
          "type": "number"
        },
        "fastingGlucose": {
          "type": "number"
        },
        "exerciseFreq": {
          "type": "integer"
        },
        "dietType": {
          "type": "string"
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "risk_data": {
      "type": "object",
      "properties": {
        "riskLevel": {
          "type": "string"
        },
        "riskScore": {
          "type": "integer"
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "checkin_data": {
      "type": "object",
      "properties": {
        "recent_days": {
          "type": "integer"
        },
        "total_recent": {
          "type": "integer"
        },
        "records": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {},
            "required": [],
            "additionalProperties": true
          }
        }
      },
      "required": [],
      "additionalProperties": true
    }
  },
  "required": [],
  "additionalProperties": true
}
```

**HTTP 请求体示例：**

```json
{
  "response_mode": "blocking",
  "user": "usr_001",
  "inputs": {
    "query": "请根据用户健康画像生成个性化健康管理方案",
    "user_id": "usr_001",
    "daily_calories": 1800,
    "user_profile": {
      "age": 35,
      "gender": "male",
      "nickname": "张三"
    },
    "health_profile": {
      "height": 170,
      "weight": 72.5,
      "bmi": 25.1,
      "fastingGlucose": 6.8,
      "exerciseFreq": 2,
      "dietType": "balanced"
    },
    "risk_data": {
      "riskLevel": "medium",
      "riskScore": 45
    },
    "checkin_data": {
      "recent_days": 14,
      "total_recent": 5,
      "records": []
    }
  }
}
```

**`inputs` 内各字段：**

```json
{
  "query": "请根据用户健康画像生成个性化健康管理方案",
  "user_id": "usr_001",
  "daily_calories": 1800,
  "user_profile": {
    "age": 35,
    "gender": "male",
    "nickname": "张三"
  },
  "health_profile": {
    "height": 170,
    "weight": 72.5,
    "bmi": 25.1,
    "fastingGlucose": 6.8,
    "exerciseFreq": 2,
    "dietType": "balanced"
  },
  "risk_data": {
    "riskLevel": "medium",
    "riskScore": 45
  },
  "checkin_data": {
    "recent_days": 14,
    "total_recent": 5,
    "records": []
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `query` | string | 固定方案生成请求文案 |
| `user_id` | string | 当前用户 ID |
| `daily_calories` | integer | Mifflin-St Jeor 公式计算的每日推荐热量 (kcal) |
| `user_profile` | object | 用户基本信息（昵称、性别、年龄等） |
| `health_profile` | object | 最新健康档案 |
| `risk_data` | object | 最新风险评估（选填），无评估时不传该字段 |
| `checkin_data` | object | 近 14 天打卡摘要 |
| `checkin_data.recent_days` | integer | 统计天数 |
| `checkin_data.total_recent` | integer | 近期打卡总次数 |
| `checkin_data.records` | array | 打卡记录列表 |

### 4.2 工作流需返回（`outputs.plan_llm_output`，浅层 LLM 输出）

> 与 `backend/plan-service/src/main/resources/dify/plan-generation-llm-output.schema.json` 一致。  
> **因 Dify JSON 深度限制为 5**，工作流结束节点只输出 LLM Structured Output（浅层字段），**不在 Dify 内组装**嵌套 `health_plan`。  
> 后端 `DifyPlanLlmOutputAssembler` 接收 `plan_llm_output` 后组装为完整方案。

**Dify 结束节点配置：**

| 输出变量名 | 类型 | 值 |
|-----------|------|-----|
| `plan_llm_output` | Object | 直接引用 LLM 节点的 `structured_output`（或兜底 LLM 的输出） |

**HTTP 响应示例：**

```json
{
  "data": {
    "status": "succeeded",
    "outputs": {
      "plan_llm_output": {
        "summary": "基于用户画像生成的方案概述",
        "breakfast_time": "07:30",
        "breakfast_foods": [
          { "name": "燕麦片", "amount": "40g", "calories": 150, "gi_level": "low" }
        ],
        "breakfast_total_calories": 370,
        "lunch_time": "12:00",
        "lunch_foods": [],
        "lunch_total_calories": 500,
        "dinner_time": "18:30",
        "dinner_foods": [],
        "dinner_total_calories": 500,
        "snack_time": "15:00",
        "snack_foods": [],
        "snack_total_calories": 100,
        "diet_principles": ["控制总热量摄入，每日约1800kcal"],
        "foods_to_avoid": ["含糖饮料"],
        "foods_to_recommend": ["全谷物"],
        "exercise_weekly_goal": "每周至少150分钟中等强度有氧运动",
        "exercise_items": [
          {
            "type": "快走",
            "duration": "30分钟",
            "frequency": "每日",
            "intensity": "中等",
            "calories_burned": 150,
            "caution": "餐后1小时进行"
          }
        ],
        "rest_wake_up": "06:30",
        "rest_sleep": "22:30",
        "rest_nap": "午休20-30分钟",
        "rest_glucose_monitor_times": ["空腹", "早餐后2h", "睡前"],
        "rest_routine_tips": ["固定作息时间"],
        "medication_note": "请遵医嘱按时用药。请在医生指导下执行。"
      }
    }
  }
}
```

**LLM Structured Output / 结束节点 JSON Schema：**

> 完整 Schema 见 `plan-generation-llm-output.schema.json`；也可直接使用 LLM 节点 Structured Output 配置（深度 ≤4）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `summary` | string | 方案概述 |
| `diet_principles` | string[] | 饮食原则 |
| `foods_to_avoid` / `foods_to_recommend` | string[] | 禁忌 / 推荐食物 |
| `breakfast_time` / `breakfast_foods` / `breakfast_total_calories` | string / array / integer | 早餐 |
| `lunch_*` / `dinner_*` / `snack_*` | 同上 | 午餐 / 晚餐 / 加餐 |
| `exercise_weekly_goal` | string | 运动周目标 |
| `exercise_items` | array | 运动项（type、duration、frequency 等） |
| `rest_wake_up` / `rest_sleep` / `rest_nap` | string | 作息 |
| `rest_glucose_monitor_times` / `rest_routine_tips` | string[] | 监测时点 / 作息建议 |
| `medication_note` | string | 用药注意事项 |

### 4.3 后端组装后的完整方案结构（参考）

以下为 plan-service 组装后内部使用的结构（**不必**在 Dify 结束节点输出）：

```json
{
  "summary": "基于用户画像生成的方案概述",
  "diet_plan": {
    "meal_plan": {
      "breakfast": {
        "time": "07:00-08:00",
        "foods": [
          { "name": "全麦面包", "amount": "2片", "calories": 160, "gi_level": "low" }
        ],
        "total_calories": 310
      },
      "lunch": { "time": "12:00-13:00", "foods": [], "total_calories": 390 },
      "dinner": { "time": "18:00-19:00", "foods": [], "total_calories": 270 },
      "snack": { "time": "15:00-16:00", "foods": [], "total_calories": 160 }
    },
    "diet_principles": ["控制总热量摄入，每日约1800kcal", "选择低GI食物"],
    "foods_to_avoid": ["含糖饮料", "糕点甜点"],
    "foods_to_recommend": ["全谷物", "绿叶蔬菜"]
  },
  "exercise_plan": {
    "weekly_goal": "每周至少150分钟中等强度有氧运动",
    "items": [
      {
        "type": "快走",
        "duration": "30分钟",
        "frequency": "每日",
        "intensity": "中等",
        "calories_burned": 150,
        "caution": "餐后1小时进行"
      }
    ]
  },
  "rest_plan": {
    "wake_up": "06:30",
    "sleep": "22:30",
    "nap": "午休20-30分钟",
    "glucose_monitor_times": ["空腹", "早餐后2h", "晚餐前", "睡前"],
    "routine_tips": ["固定作息时间，避免熬夜"]
  },
  "medication_note": "请遵医嘱按时用药，注意监测低血糖症状。请在医生指导下执行。"
}
```

**完整 nested Schema（仅供后端参考）：**

```json
{
  "type": "object",
  "properties": {
    "summary": {
      "type": "string"
    },
    "diet_plan": {
      "type": "object",
      "properties": {
        "meal_plan": {
          "type": "object",
          "properties": {
            "breakfast": {
              "type": "object",
              "properties": {
                "time": { "type": "string" },
                "foods": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "amount": { "type": "string" },
                      "calories": { "type": "integer" },
                      "gi_level": { "type": "string" }
                    },
                    "required": [],
                    "additionalProperties": true
                  }
                },
                "total_calories": { "type": "integer" }
              },
              "required": [],
              "additionalProperties": true
            },
            "lunch": {
              "type": "object",
              "properties": {
                "time": { "type": "string" },
                "foods": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "amount": { "type": "string" },
                      "calories": { "type": "integer" },
                      "gi_level": { "type": "string" }
                    },
                    "required": [],
                    "additionalProperties": true
                  }
                },
                "total_calories": { "type": "integer" }
              },
              "required": [],
              "additionalProperties": true
            },
            "dinner": {
              "type": "object",
              "properties": {
                "time": { "type": "string" },
                "foods": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "amount": { "type": "string" },
                      "calories": { "type": "integer" },
                      "gi_level": { "type": "string" }
                    },
                    "required": [],
                    "additionalProperties": true
                  }
                },
                "total_calories": { "type": "integer" }
              },
              "required": [],
              "additionalProperties": true
            },
            "snack": {
              "type": "object",
              "properties": {
                "time": { "type": "string" },
                "foods": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "amount": { "type": "string" },
                      "calories": { "type": "integer" },
                      "gi_level": { "type": "string" }
                    },
                    "required": [],
                    "additionalProperties": true
                  }
                },
                "total_calories": { "type": "integer" }
              },
              "required": [],
              "additionalProperties": true
            }
          },
          "required": [],
          "additionalProperties": true
        },
        "diet_principles": {
          "type": "array",
          "items": { "type": "string" }
        },
        "foods_to_avoid": {
          "type": "array",
          "items": { "type": "string" }
        },
        "foods_to_recommend": {
          "type": "array",
          "items": { "type": "string" }
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "exercise_plan": {
      "type": "object",
      "properties": {
        "weekly_goal": { "type": "string" },
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "type": { "type": "string" },
              "duration": { "type": "string" },
              "frequency": { "type": "string" },
              "intensity": { "type": "string" },
              "calories_burned": { "type": "integer" },
              "caution": { "type": "string" }
            },
            "required": [],
            "additionalProperties": true
          }
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "rest_plan": {
      "type": "object",
      "properties": {
        "wake_up": { "type": "string" },
        "sleep": { "type": "string" },
        "nap": { "type": "string" },
        "glucose_monitor_times": {
          "type": "array",
          "items": { "type": "string" }
        },
        "routine_tips": {
          "type": "array",
          "items": { "type": "string" }
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "medication_note": {
      "type": "string"
    }
  },
  "required": [],
  "additionalProperties": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `summary` | string | 推荐 | 方案概述 |
| `diet_plan` | object | **是** | 饮食方案（含 `meal_plan`、原则、推荐/禁忌食物） |
| `exercise_plan` | object | **是** | 运动方案 |
| `rest_plan` | object | **是** | 作息与监测安排 |
| `medication_note` | string | **是** | 用药注意事项 |

---

## 5. 糖尿病风险评估

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/health-service/.../DifyRiskAssessmentWorkflowContract.java` |
| 入参 Schema 文件 | `backend/health-service/src/main/resources/dify/risk-assessment-input.schema.json` |
| 默认 API Key | `app-XuGJ0yp3XhejVrzE88dL1kHk` |
| 触发模式 | `DIFY_RISK_TRIGGER_MODE=api`（Workflow API）或 `webhook` |

### 5.1 传入数据 JSON Schema（开始节点 5 字段平铺）

> 与 `backend/health-service/src/main/resources/dify/risk-assessment-input.schema.json` 一致。  
> 默认 `DIFY_RISK_INPUT_VAR=flat`：开始节点 5 个独立变量，后端平铺写入 API `inputs`。  
> **`user_profile`、`questionnaire`、`medical_calc_results`、`risk_factors` 在 HTTP 请求中为 JSON 文本字符串**。

**开始节点 Schema（各变量类型）：**

```json
{
  "type": "object",
  "properties": {
    "user_id": { "type": "string" },
    "user_profile": { "type": "string" },
    "questionnaire": { "type": "string" },
    "medical_calc_results": { "type": "string" },
    "risk_factors": { "type": "string" }
  },
  "required": [],
  "additionalProperties": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | string | 当前用户 ID |
| `user_profile` | string | 用户基本信息 JSON 字符串（含 `age`、`gender` 等） |
| `questionnaire` | string | 问卷全量字段 JSON 字符串（`RiskAssessRequest` 序列化，camelCase） |
| `medical_calc_results` | string | 医学计算器输出 JSON 字符串 |
| `risk_factors` | string | 基础风险因素 JSON 数组字符串 |

**`user_profile` 解析后对象示例：**

```json
{ "age": 48, "gender": "male", "nickname": "张先生" }
```

**`questionnaire` 解析后字段（节选）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `height` / `weight` | number | 身高(cm)、体重(kg) |
| `fastingGlucose` | number | 空腹血糖 mmol/L |
| `systolicBp` / `diastolicBp` | integer | 血压 mmHg |
| `familyHistory` | boolean | 是否有家族史 |
| `smoking` | integer | 0从未 / 1已戒 / 2当前吸烟 |
| `exerciseFreq` | integer | 0无 / 1低 / 2中 / 3高 |
| `dietType` | string | 如 `high-sugar`、`balanced` |

**`medical_calc_results` 解析后：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `bmi` | number | BMI 值 |
| `bmiLevel` | string | `underweight` / `normal` / `overweight` / `obese` |
| `glucoseLevel` | string | `normal` / `prediabetes` / `diabetes` |
| `baseRiskScore` | integer | 基础风险分 0~100 |

**`risk_factors` 解析后数组元素：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `factor_code` | string | 因素编码（可选） |
| `name` | string | 因素名称 |
| `weight` | number | 权重 0~100 |
| `factor_level` | integer | 因素等级 1低 / 2中 / 3高 |
| `description` | string | 说明 |

**HTTP 请求体示例（flat 模式，默认）：**

```json
{
  "response_mode": "blocking",
  "user": "usr_001",
  "inputs": {
    "user_id": "usr_001",
    "user_profile": "{\"age\":48,\"gender\":\"male\"}",
    "questionnaire": "{\"height\":170,\"weight\":82,\"fastingGlucose\":6.5,\"postprandialGlucose\":8.2,\"hba1c\":6.1,\"systolicBp\":138,\"diastolicBp\":88,\"familyHistory\":true,\"smoking\":1,\"alcohol\":0,\"exerciseFreq\":1,\"dietType\":\"high-sugar\",\"isPregnant\":false,\"medicalHistories\":[],\"medications\":[],\"familyHistories\":[]}",
    "medical_calc_results": "{\"bmi\":28.4,\"bmiLevel\":\"obese\",\"glucoseLevel\":\"prediabetes\",\"baseRiskScore\":75}",
    "risk_factors": "[{\"name\":\"BMI超标\",\"weight\":20,\"description\":\"BMI=28.4，属于obese\"},{\"name\":\"血糖偏高\",\"weight\":25,\"description\":\"空腹血糖6.5mmol/L\"}]"
  }
}
```

**业务 payload 可读形式（后端序列化前的逻辑结构，便于对照）：**

```json
{
  "user_id": "usr_001",
  "user_profile": { "age": 48, "gender": "male" },
  "questionnaire": {
    "height": 170,
    "weight": 82,
    "fastingGlucose": 6.5,
    "postprandialGlucose": 8.2,
    "hba1c": 6.1,
    "systolicBp": 138,
    "diastolicBp": 88,
    "familyHistory": true,
    "smoking": 1,
    "alcohol": 0,
    "exerciseFreq": 1,
    "dietType": "high-sugar",
    "isPregnant": false,
    "medicalHistories": [],
    "medications": [],
    "familyHistories": []
  },
  "medical_calc_results": {
    "bmi": 28.4,
    "bmiLevel": "obese",
    "glucoseLevel": "prediabetes",
    "baseRiskScore": 75
  },
  "risk_factors": [
    { "name": "BMI超标", "weight": 20, "description": "BMI=28.4，属于obese" },
    { "name": "血糖偏高", "weight": 25, "description": "空腹血糖6.5mmol/L" }
  ]
}
```

> 后端 `DifyRiskAssessmentWorkflowContract.buildInputObject` 将 `user_profile`、`questionnaire`、`medical_calc_results`、`risk_factors` 序列化为 JSON 字符串后写入 Dify `inputs`。  
> 若 `DIFY_RISK_INPUT_VAR=inputs`（单变量包裹），请将上述可读形式整体作为 `inputs.inputs` 的一个 Object；**flat 模式（默认）下各 JSON 字段必须为字符串**。

### 5.2 工作流需返回（`outputs.risk_assessment`）

```json
{
  "risk_score": 45,
  "risk_level": "medium",
  "confidence": "high",
  "analysis": "综合评估结果显示，用户目前处于糖尿病中度风险状态。...",
  "glucose_level": "prediabetes",
  "risk_factors": [
    {
      "factor_code": "bmi",
      "name": "BMI超标",
      "weight": 30,
      "factor_level": 2,
      "description": "BMI=24.5，属于超重范围"
    }
  ],
  "suggestions": [
    { "category": 1, "priority": 1, "content": "控制饮食，减少高糖高脂食物摄入" },
    "也可为纯字符串"
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `risk_score` | integer | 风险评分 0~100 |
| `risk_level` | string | `low` / `medium` / `high` |
| `confidence` | string | `low` / `medium` / `high` |
| `analysis` | string | AI 深度分析文本 |
| `glucose_level` | string | `normal` / `prediabetes` / `diabetes` |
| `risk_factors[]` | array | 风险因素（含 `factor_code`、`name`、`weight`、`factor_level`、`description`） |
| `suggestions[]` | array | 改善建议；元素可为 `{ category, priority, content }` 或纯字符串 |

> 设计文档中的 `chart_data`（雷达图/仪表盘）为可选字段，当前后端未强制解析。

---

## 6. 资讯个性化推荐

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/article-service/.../DifyArticleRecommendWorkflowContract.java` |
| 入参 Schema 文件 | `backend/article-service/src/main/resources/dify/article-recommend-input.schema.json` |
| 默认 API Key | `app-Af1Iv6Y51WrahbDXQMBDUy7f` |
| 调用场景 | 推荐 Phase 4：对 Top-N 候选文章 AI 重排 |
| 入参模式 | **flat**（开始节点 4 变量平铺，各字段为 JSON 文本字符串） |

### 6.1 传入数据（开始节点平铺）

> 与 `article-recommend-input.schema.json` 一致。除结构化字段外，**各变量值均为 JSON 文本字符串**（Paragraph / Text-input 类型）。

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `user_profile` | string (JSON) | 用户兴趣画像 |
| `health_profile` | string (JSON) | 健康档案摘要 |
| `risk_profile` | string (JSON) | 风险评估摘要 |
| `candidate_articles` | string (JSON 数组) | 待重排候选文章 Top-N |

**`user_profile` 解析后结构：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | string | 用户 ID |
| `interest_tags` | string[] | 兴趣标签（来自收藏/阅读文章标签） |
| `category_weights` | object | 分类 slug → 归一化权重，如 `diet: 0.35` |

**`health_profile` 解析后结构（示例）：**

```json
{
  "user_id": "user_001",
  "diabetes_type": "2型",
  "age": 52,
  "gender": "male",
  "height": 172,
  "weight": 78,
  "bmi": 26.4,
  "fasting_glucose": 7.2,
  "postprandial_glucose": 11.5,
  "hba1c": 7.8,
  "systolic_bp": 135,
  "diastolic_bp": 85,
  "activity_level": "moderate",
  "diet_habit": "high_carb"
}
```

**`risk_profile` 解析后结构（示例）：**

```json
{
  "user_id": "user_001",
  "risk_level": "high",
  "risk_score": 72,
  "risk_factors": ["空腹血糖偏高(7.2mmol/L)", "糖化血红蛋白超标(7.8%)"],
  "primary_risk": "血糖控制不佳",
  "secondary_risk": "心血管风险"
}
```

**`candidate_articles` 数组元素：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `article_id` | string | 文章 ID |
| `title` | string | 标题 |
| `summary` | string | 摘要 |
| `category` | string | 分类 slug：`diet` / `exercise` / `medication` / `diabetes_basics` / `complications` |
| `tags` | string[] | 标签 |
| `score` | number | 本地 Phase 1~3 规则打分 |

**完整入参示例（Dify Workflow API `inputs`）：**

```json
{
  "user_profile": "{\"user_id\":\"user_001\",\"interest_tags\":[\"饮食管理\",\"血糖监测\"],\"category_weights\":{\"diet\":0.35,\"exercise\":0.25}}",
  "health_profile": "{\"user_id\":\"user_001\",\"diabetes_type\":\"2型\",\"bmi\":26.4,\"fasting_glucose\":7.2}",
  "risk_profile": "{\"user_id\":\"user_001\",\"risk_level\":\"high\",\"risk_score\":72}",
  "candidate_articles": "[{\"article_id\":\"art_001\",\"title\":\"...\",\"category\":\"diet\",\"score\":92}]"
}
```

> 后端 `DifyArticleRecommendWorkflowContract.buildInputObject` 自动将健康档案、风险评估从 camelCase 转为 snake_case，并将分类 ID 映射为 slug。

### 6.2 工作流需返回（`outputs.article_info`）

```json
{
  "article_info": {
    "recommendations": [
      {
        "article_id": "art_001",
        "rec_reason": "结合您的 BMI 与血糖情况，本文饮食方案可针对性降低餐后血糖峰值。"
      }
    ]
  },
  "has_error": false,
  "error_message": "",
  "source": "糖尿病知识库文档"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `article_info.recommendations[]` | array | 重排后的推荐列表（顺序即优先级） |
| `recommendations[].article_id` | string | 文章 ID |
| `recommendations[].rec_reason` | string | 个性化推荐理由 |
| `has_error` | boolean | 可选，是否出错 |
| `error_message` | string | 可选，错误信息 |

> 后端也兼容 `outputs.recommendations`、`outputs.articles`、`outputs.text` 内嵌 JSON 等旧格式。

---

## 7. 健康趋势分析

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/user-service/.../DifyHealthTrendWorkflowContract.java` |
| 业务服务 | `HealthTrendAnalysisService` |
| 默认 API Key | `DIFY_HEALTH_TREND_API_KEY` |
| 对外 API | `GET /api/v1/user/health-trend` |

### 7.1 传入数据（开始节点 3 字段平铺）

| 字段 | 类型 | 说明 |
|------|------|------|
| `query` | string | 固定「请分析用户近期的健康指标变化趋势」 |
| `health_history` | string | 近 N 条（默认 30）健康记录 **JSON 数组字符串** |
| `user_baseline` | string | 最新一条健康档案 **JSON 对象字符串** |

**HTTP 请求体示例：**

```json
{
  "response_mode": "blocking",
  "user": "usr_001",
  "inputs": {
    "query": "请分析用户近期的健康指标变化趋势",
    "health_history": "[{\"recordId\":\"hr_001\",\"recordedAt\":\"2024-05-11T08:00:00\",\"height\":170,\"weight\":71,\"bmi\":24.1,\"fastingGlucose\":5.2,\"systolicBp\":120,\"diastolicBp\":80}]",
    "user_baseline": "{\"recordId\":\"hr_004\",\"recordedAt\":\"2024-06-10T08:00:00\",\"height\":170,\"weight\":70.5,\"bmi\":24.2,\"fastingGlucose\":6.8,\"systolicBp\":116,\"diastolicBp\":76}"
  }
}
```

**`health_history` 数组元素字段：** `recordId`、`recordedAt`、`height`、`weight`、`bmi`、`fastingGlucose`、`postprandialGlucose`、`systolicBp`、`diastolicBp`（camelCase，与 `HealthRecord` 实体对齐）。

### 7.2 工作流需返回（`outputs.trend_analysis`）

```json
{
  "summary": "近30天健康趋势分析：血糖水平呈上升趋势...",
  "risk_level": "attention",
  "bmi_trend": {
    "direction": "stable",
    "avg_value": 24.2,
    "change_rate": 0.5,
    "data_points": [{ "date": "2024-05-11", "value": 24.1 }]
  },
  "glucose_trend": {
    "direction": "rising",
    "avg_value": 6.1,
    "change_rate": 15.4,
    "data_points": [{ "date": "2024-05-11", "value": 5.2 }]
  },
  "bp_trend": {
    "direction": "stable",
    "avg_systolic": 118,
    "avg_diastolic": 78,
    "data_points": [{ "date": "2024-05-11", "systolic": 120, "diastolic": 80 }]
  },
  "anomalies": [{
    "type": "glucose",
    "date": "2024-06-10",
    "value": 6.8,
    "severity": "warning",
    "description": "空腹血糖6.8mmol/L，超过正常上限(6.1mmol/L)",
    "suggestion": "建议复查空腹血糖，如持续偏高请内分泌科就诊"
  }]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `summary` | string | 趋势总结（80~300 字） |
| `risk_level` | string | `normal` / `attention` / `warning` / `critical` |
| `bmi_trend` / `glucose_trend` | object | `direction`、`avg_value`、`change_rate`、`data_points[]` |
| `bp_trend` | object | 含 `avg_systolic`、`avg_diastolic`、`data_points[]`（`systolic`/`diastolic`） |
| `anomalies[]` | array | `type`（`glucose`/`bmi`/`bp`）、`severity`（`info`/`warning`/`critical`）等 |

**降级：** 未配置 API Key、Dify 失败或历史记录 &lt; 2 条时不阻断主流程，返回本地折线或固定提示。

---

## 8. AI 模拟医生问诊

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/consultation-service/.../DifyConsultationWorkflowContract.java` |
| 业务服务 | `ConsultationService` / `AIDoctorService` |
| 默认 API Key | `DIFY_CONSULTATION_API_KEY` |
| 超时建议 | `DIFY_CONSULTATION_TIMEOUT_SECONDS` ≥ 120 |

### 8.1 传入数据（开始节点 6 字段平铺）

| 字段 | 类型 | 说明 |
|------|------|------|
| `query` | string | 用户最新消息原文 |
| `conversation_id` | string | 咨询会话 ID |
| `doctor_role` | string | AI 医生角色设定（科室、擅长、语气） |
| `patient_profile` | string | 健康档案 JSON 字符串 |
| `conversation_history` | string | 最近 20 条消息拼接文本 |
| `knowledge_context` | string | 诊疗指南 / Milvus 检索片段 |

**HTTP 请求体示例：**

```json
{
  "response_mode": "blocking",
  "user": "usr_001",
  "inputs": {
    "query": "大概两个月了，确实口渴明显，夜尿增多，需要做什么检查？",
    "conversation_id": "sess_a1b2c3d4",
    "doctor_role": "你是一名资深内分泌科主任医师，擅长糖尿病及代谢综合征诊疗...",
    "patient_profile": "{\"age\":52,\"gender\":\"male\",\"height\":170,\"weight\":78,\"fastingGlucose\":7.2}",
    "conversation_history": "[用户 2024-06-10 10:00] 医生您好...\n[AI医生 2024-06-10 10:01] 感谢您的描述...",
    "knowledge_context": "【片段1 | 中国2型糖尿病防治指南 | 相似度:0.93】\n空腹血糖≥7.0mmol/L..."
  }
}
```

> 问诊安全规则、免责声明写在 **LLM 系统提示词**中，不作为开始节点入参。

### 8.2 工作流需返回（`outputs.doctor_reply`）

```json
{
  "content": "根据您描述的情况，建议您：\n1. 完善糖耐量试验（OGTT）...\n\n⚠️ 以上建议仅供参考，不能替代线下就医。",
  "suggestion": {
    "possible_diagnoses": [
      { "name": "2型糖尿病", "probability": "high" },
      { "name": "糖耐量异常", "probability": "medium" }
    ],
    "suggested_questions": ["您是否有多饮、多尿的症状？"],
    "recommended_exams": ["空腹血糖", "OGTT", "糖化血红蛋白"],
    "treatment_strategy": "生活方式干预为基础，必要时启动口服降糖药物治疗"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 是 | 面向用户的 Markdown 回复 |
| `suggestion` | object | 否 | 结构化辅助建议（供 `ai-suggestion` API） |
| `suggestion.possible_diagnoses[].probability` | string | — | `high` / `medium` / `low` |

---

## 9. 科普问答

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/home-service/.../DifyQaChatContract.java` |
| 业务服务 | `AIChatService` |
| 默认 API Key | `DIFY_QA_API_KEY` |
| 对外 API | `POST /api/v1/chat/qa`（SSE） |
| Milvus | 后端 `KnowledgeRetrieval` 预检索 Top-5，`docType=guideline` |

> **实现说明：** 使用 Dify **Workflow API**（`/v1/workflows/run`，`streaming`），非 Chatbot API。Milvus 检索在**后端**完成，结果写入 `knowledge_context`。

### 9.1 传入数据（开始节点 2 字段平铺）

| 字段 | 类型 | 说明 |
|------|------|------|
| `query` | string | 用户科普问题，≤ 500 字符 |
| `knowledge_context` | string | Milvus Top-5 片段拼接文本 |

**`knowledge_context` 格式示例：**

```text
【片段1 | 来源: 糖尿病饮食指南.pdf | 相似度: 0.956】
糖尿病患者应控制碳水化合物摄入...

【片段2 | 来源: 中国2型糖尿病防治指南 | 相似度: 0.912】
每日膳食纤维摄入建议 25~30g...
```

**HTTP 请求体示例：**

```json
{
  "response_mode": "streaming",
  "user": "usr_001",
  "inputs": {
    "query": "糖尿病患者可以吃水果吗？哪些水果比较适合？",
    "knowledge_context": "【片段1 | 来源: 糖尿病饮食指南.pdf | 相似度: 0.956】\n糖尿病患者可以适量吃水果..."
  }
}
```

### 9.2 工作流结束节点输出（平铺 5 字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `valid` | boolean | 输入校验是否通过 |
| `message` | string | 校验说明 |
| `error_message` | string | 校验失败时的用户可见错误 |
| `error_type` | string | 错误类型标识 |
| `text` | string | 校验通过后的 Markdown 科普回答 |

**blocking 调试响应示例：**

```json
{
  "data": {
    "status": "succeeded",
    "outputs": {
      "valid": true,
      "message": "校验通过",
      "error_message": "",
      "error_type": "",
      "text": "## 糖尿病患者可以吃水果吗？\n\n可以适量食用..."
    }
  }
}
```

**后端 SSE 转发格式：** `event: message` + `{"type":"text","content":"..."}`；`event: message_end` + `{"type":"end","metadata":{"sources":[...]}}`。

---

## 10. 食物图片识别

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/checkin-service/.../DifyFoodRecognitionWorkflowContract.java` |
| 业务服务 | `FoodRecognitionService` |
| 默认 API Key | `DIFY_FOOD_RECOGNITION_API_KEY` |
| 规划 API | `POST /api/v1/checkin/food/recognize` |
| 图片 URL | `DIFY_FOOD_RECOGNITION_IMAGE_PUBLIC_BASE_URL` + MinIO objectKey（须 Dify 可达） |

### 10.1 传入数据（开始节点 6 字段平铺）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | 是 | 固定识别请求文案 |
| `user_id` | string | 是 | 当前用户 ID |
| `image_url` | string | 是 | MinIO 公开 URL |
| `meal_period` | string | 否 | 餐次 1~6（Dify 开始节点为 text-input，传字符串） |
| `food_categories` | string | 否 | 分类 JSON 数组字符串 |
| `food_presets` | string | 否 | 预设食物 JSON 数组字符串 |

**HTTP 请求体示例：**

```json
{
  "response_mode": "blocking",
  "user": "usr_001",
  "inputs": {
    "query": "请识别图片中的食物，返回名称、分类、营养参数及建议食用量，便于糖尿病用户饮食打卡。",
    "user_id": "usr_001",
    "image_url": "http://localhost:9000/checkin/food/usr_001/upload_abc123.jpg",
    "meal_period": "2",
    "food_categories": "[{\"category_id\":\"cat_grain\",\"category_name\":\"主食\"}]",
    "food_presets": "[{\"food_id\":\"food_rice_001\",\"food_name\":\"糙米饭\",\"calories_per_gram\":1.16}]"
  }
}
```

### 10.2 工作流需返回（`outputs.food_recognition`）

**成功示例：**

```json
{
  "food_name": "糙米饭",
  "category_id": "cat_grain",
  "category_name": "主食",
  "calories_per_gram": 1.16,
  "is_liquid": false,
  "ml_to_g_ratio": 1.0,
  "suggested_input_unit": 1,
  "suggested_input_amount": 150,
  "suggested_grams": 150,
  "suggested_total_calories": 174,
  "matched_food_id": "food_rice_001",
  "source_type": 1,
  "confidence": "high",
  "gi_level": "medium",
  "nutrition_tip": "糙米饭升糖较白米饭慢，建议控制在一碗以内并搭配蔬菜。",
  "recognition_summary": "识别为一碗糙米饭，估算约 150g。",
  "items": [],
  "has_error": false,
  "error_message": ""
}
```

| 字段 | 说明 |
|------|------|
| 核心预填 | `food_name`、`category_id`、`calories_per_gram`、`is_liquid`、`suggested_input_*`、`suggested_grams`、`suggested_total_calories` |
| 预设匹配 | `matched_food_id`；`source_type`：1=预设，2=自定义 |
| AI 展示 | `confidence`、`gi_level`、`nutrition_tip`、`recognition_summary`、`items[]` |
| 错误 | `has_error`、`error_message`（识别失败时不抛 HTTP 错误） |

> AI 结果**不直接写库**；用户确认后仍调用 `POST /api/v1/checkin/food`。核心 5 营养字段与 `USER_FOOD_PRESETS` 表对齐。

---

## 11. 资讯初稿生成

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/article-service/.../DifyArticleDraftWorkflowContract.java` |
| 调用方 | **管理端前端直调** Dify；后端 `GET /api/v1/admin/articles/ai-draft/config` 下发 URL 与 Key |
| 默认 API Key | `DIFY_ARTICLE_DRAFT_API_KEY` |
| 可选 URL 覆盖 | `DIFY_ARTICLE_DRAFT_URL` |

### 11.1 传入数据（开始节点 2 字段平铺）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `topic` | string | 是 | 资讯主题，如「糖尿病饮食管理」 |
| `keywords` | string | 否 | 关键词，逗号分隔 |

**HTTP 请求体示例（streaming）：**

```json
{
  "response_mode": "streaming",
  "user": "admin_001",
  "inputs": {
    "topic": "糖尿病饮食管理",
    "keywords": "糖尿病,饮食,GI值,血糖控制,低糖"
  }
}
```

### 11.2 工作流需返回

**流式 SSE 事件（管理端约定）：**

| event | 说明 |
|-------|------|
| `text_chunk` | 正文增量；`data` 含 `title`、`summary`、`content`、`tags` |
| `complete` | 生成完成，携带完整初稿 |
| `error` | `{ "code", "message" }` |

**blocking 调试（`outputs.article_draft`）：**

```json
{
  "title": "糖尿病患者的科学饮食指南",
  "summary": "本文详细介绍了糖尿病患者如何通过科学饮食控制血糖...",
  "content": "# 糖尿病患者的科学饮食指南\n\n## 一、饮食原则\n...",
  "tags": ["糖尿病", "饮食", "血糖控制", "营养"]
}
```

> 封面图不由工作流生成；保存草稿时管理员自行上传。知识库检索与写作专家角色在 **Dify 工作流内**配置。

---

## 12. 环境变量速查

```bash
# 公共
DIFY_BASE_URL=http://host.docker.internal:58080
DIFY_INTERNAL_KEY=LoginAuth2026          # Dify 回调后端内部 API（Header: X-Dify-Key）

# 打卡行为分析（开始节点 7 变量平铺）
DIFY_CHECKIN_API_KEY=app-kFnPhD8YUmCfG2DeHubp1Scc
DIFY_CHECKIN_RESPONSE_MODE=blocking

# 健康方案生成（开始节点 7 变量平铺）
DIFY_PLAN_API_KEY=app-fHORDdQjVq9GVtLcyD9WaL3g
DIFY_PLAN_RESPONSE_MODE=blocking

# 风险评估
DIFY_RISK_API_KEY=app-XuGJ0yp3XhejVrzE88dL1kHk
DIFY_RISK_INPUT_VAR=flat
DIFY_RISK_INPUT_FORMAT=object
DIFY_RISK_TRIGGER_MODE=api
DIFY_RISK_RESPONSE_MODE=blocking

# 健康趋势分析（user-service，开始节点 3 变量平铺）
DIFY_HEALTH_TREND_API_KEY=app-xxxxxxxx
DIFY_HEALTH_TREND_RESPONSE_MODE=blocking

# 资讯推荐
DIFY_ARTICLE_RECOMMEND_API_KEY=app-Af1Iv6Y51WrahbDXQMBDUy7f
DIFY_ARTICLE_RECOMMEND_INPUT_VAR=flat
DIFY_ARTICLE_RECOMMEND_INPUT_FORMAT=object
DIFY_ARTICLE_RECOMMEND_RESPONSE_MODE=blocking

# 资讯初稿（管理端直调 / article-service config）
DIFY_ARTICLE_DRAFT_API_KEY=app-xxxxxxxx
DIFY_ARTICLE_DRAFT_URL=                    # 可选，覆盖 workflowUrl

# 食物图片识别（checkin-service，开始节点 6 变量平铺）
DIFY_FOOD_RECOGNITION_API_KEY=app-xxxxxxxx
DIFY_FOOD_RECOGNITION_RESPONSE_MODE=blocking
DIFY_FOOD_RECOGNITION_IMAGE_PUBLIC_BASE_URL=   # Dify 拉取图片用公网 URL

# 科普问答（home-service，Workflow streaming）
DIFY_QA_API_KEY=app-xxxxxxxx

# AI 模拟医生问诊（consultation-service，开始节点 6 变量平铺）
DIFY_CONSULTATION_API_KEY=app-xxxxxxxx
DIFY_CONSULTATION_RESPONSE_MODE=blocking
DIFY_CONSULTATION_TIMEOUT_SECONDS=120

# 语音识别 — 生产用 DashScope Fun-ASR（非 Dify）
DASHSCOPE_API_KEY=sk-xxxxxxxx
DASHSCOPE_STT_MODEL=fun-asr
STT_MAX_AUDIO_BYTES=5242880
STT_AUDIO_PUBLIC_BASE_URL=http://localhost:9000
```

---

## 13. Schema 文件与契约类对照

| 工作流 | Schema 文件 | 契约类 |
|--------|-------------|--------|
| 打卡行为分析 | `backend/checkin-service/src/main/resources/dify/checkin-analysis-input.schema.json` | `DifyCheckinAnalysisWorkflowContract` |
| 健康方案生成 | 入参：`plan-generation-input.schema.json`<br>出参（LLM 浅层）：`plan-generation-llm-output.schema.json` | `DifyPlanWorkflowContract` / `DifyPlanLlmOutputAssembler` |
| 风险评估 | `backend/health-service/src/main/resources/dify/risk-assessment-input.schema.json` | `DifyRiskAssessmentWorkflowContract` |
| 资讯推荐 | `backend/article-service/src/main/resources/dify/article-recommend-input.schema.json` | `DifyArticleRecommendWorkflowContract` |
| 健康趋势分析 | 契约类内 `inputJsonSchema()` / `outputJsonSchema()` | `DifyHealthTrendWorkflowContract` |
| AI 问诊 | 契约类内 Schema | `DifyConsultationWorkflowContract` |
| 科普问答 | 契约类内 Schema | `DifyQaChatContract` |
| 食物识别 | 契约类内 Schema | `DifyFoodRecognitionWorkflowContract` |
| 资讯初稿 | 契约类内 Schema | `DifyArticleDraftWorkflowContract` |

配置 Dify 工作流时，将对应 Schema 粘贴到开始节点变量 JSON Schema 配置中；LLM Structured Output 使用各章出参 Schema。

---

## 14. 其他 Dify 相关项

| 项目 | 说明 |
|------|------|
| Dify 对话 / SSE 流式 | 科普问答、资讯初稿使用 Workflow streaming；方案生成对外 SSE 由 `plan-service` 拆分 blocking 响应推送 |
| 资讯详情 TTS | 使用阿里云百炼 `qwen3-tts-flash`（`article-service` 直连），非 Dify 工作流 |
| 主动健康干预 | 规则引擎 + 消息中心，不调用 Dify 工作流 |
