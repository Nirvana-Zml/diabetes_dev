# Dify 工作流数据契约

本文档汇总本项目所有**后端调用**的 Dify 工作流：传入数据结构、期望返回数据结构、环境变量与代码/Schema 文件位置。

> 最后更新：与 `backend/*/dify/*WorkflowContract.java` 及 `backend/*/resources/dify/*.schema.json` 保持一致。

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

| # | 业务 | 服务 | 环境变量 | 输出变量名 | 契约查询 API |
|---|------|------|----------|------------|--------------|
| 1 | 打卡 AI 行为分析 | checkin-service | `DIFY_CHECKIN_API_KEY` | `behavior_analysis` | `GET /api/v1/checkin-management/dify-workflow-spec` |
| 2 | 健康方案生成 | plan-service | `DIFY_PLAN_API_KEY` | `plan_llm_output`（浅层，后端组装） | `GET /api/v1/plan/dify-workflow-spec` |
| 3 | 糖尿病风险评估 | health-service | `DIFY_RISK_API_KEY` | `risk_assessment` | `GET /api/v1/risk/dify-workflow-spec` |
| 4 | 资讯个性化推荐 | article-service | `DIFY_ARTICLE_RECOMMEND_API_KEY` | `recommendations` | `GET /api/v1/articles/recommend/dify-workflow-spec` |

---

## 3. 打卡 AI 行为分析

| 项目 | 值 |
|------|-----|
| 契约类 | `backend/checkin-service/.../DifyCheckinAnalysisWorkflowContract.java` |
| 入参 Schema 文件 | `backend/checkin-service/src/main/resources/dify/checkin-analysis-input.schema.json` |
| 默认 API Key | `app-kFnPhD8YUmCfG2DeHubp1Scc` |

### 3.1 传入数据 JSON Schema（`inputs`，7 字段平铺）

> 与 `backend/checkin-service/src/main/resources/dify/checkin-analysis-input.schema.json` 一致。  
> **开始节点配置 7 个独立变量**（如图），后端/API 直接将各字段写入请求体 `inputs`，**不再**使用单个 `inputs` Object 包裹。  
> 专家角色与输出约束写在 **LLM 节点系统提示词**中，**不要**作为开始节点入参 `system_prompt`。

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
> **与其他工作流不同**：开始节点配置 7 个独立变量，后端直接将各字段写入 API 请求体 `inputs`，**不再**使用 `inputs.inputs` 双层嵌套。

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
> 后端 `DifyPlanLlmOutputAssembler` 接收 `plan_llm_output` 后组装为完整方案并落库。

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

> 可删除工作流内的「JSON 标准化清洗」节点；兜底 LLM 输出同样赋给 `plan_llm_output` 即可。  
> 兼容：若旧工作流仍输出嵌套 `health_plan`，后端也可解析。

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

**完整 nested Schema（仅供后端参考，勿粘贴到 Dify）：**

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
> **`user_profile`、`questionnaire`、`medical_calc_results`、`risk_factors` 在 HTTP 请求中为 JSON 文本字符串**；Dify 内可用 Code 节点 `JSON.parse` 后引用。

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
> 若 `DIFY_RISK_INPUT_VAR=inputs`（单变量包裹），请将上述可读形式整体作为 `inputs.inputs` 的一个 Object（见 §1.2）；**flat 模式（默认）下各 JSON 字段必须为字符串**。

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
| 默认 API Key | `app-zBXmLq9LXjrF8UtuL9d8PhiS` |
| 调用场景 | 推荐 Phase 4：对 Top-N 候选文章 AI 重排 |

### 6.1 传入数据 JSON Schema（`inputs.inputs`）

> 与 `backend/article-service/src/main/resources/dify/article-recommend-input.schema.json` 一致。

```json
{
  "type": "object",
  "properties": {
    "user_profile": {
      "type": "object",
      "properties": {
        "user_id": {
          "type": "string"
        },
        "interest_tags": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "category_weights": {
          "type": "object",
          "properties": {},
          "required": [],
          "additionalProperties": true
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
        }
      },
      "required": [],
      "additionalProperties": true
    },
    "risk_profile": {
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
    "candidate_articles": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "article_id": {
            "type": "string"
          },
          "title": {
            "type": "string"
          },
          "summary": {
            "type": "string"
          },
          "category": {
            "type": "string"
          },
          "tags": {
            "type": "array",
            "items": {
              "type": "string"
            }
          },
          "score": {
            "type": "number"
          }
        },
        "required": [],
        "additionalProperties": true
      }
    }
  },
  "required": [],
  "additionalProperties": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_profile` | object | 用户兴趣画像 |
| `user_profile.user_id` | string | 用户 ID |
| `user_profile.interest_tags` | string[] | 兴趣标签 |
| `user_profile.category_weights` | object | 分类 ID → 权重 |
| `health_profile` | object | 健康档案摘要 |
| `risk_profile` | object | 风险评估摘要 |
| `candidate_articles` | array | 待重排候选文章（Top-N） |
| `candidate_articles[].article_id` | string | 文章 ID |
| `candidate_articles[].title` | string | 标题 |
| `candidate_articles[].summary` | string | 摘要 |
| `candidate_articles[].category` | string | 分类 |
| `candidate_articles[].tags` | string[] | 标签 |
| `candidate_articles[].score` | number | 本地规则打分 |

### 6.2 工作流需返回（`outputs.recommendations`）

```json
{
  "recommendations": [
    {
      "article_id": "art_001",
      "rec_reason": "与您的饮食管理兴趣高度相关，适合当前控糖阶段阅读"
    },
    {
      "article_id": "art_003",
      "rec_reason": "结合您的运动偏好，提供适合糖尿病患者的运动指导"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `recommendations[]` | array | 重排后的推荐列表 |
| `recommendations[].article_id` | string | 文章 ID |
| `recommendations[].rec_reason` | string | 个性化推荐理由 |

> 后端也兼容 `outputs.articles` 或 `outputs.text` 内嵌 JSON。

---

## 7. 环境变量速查

```bash
# 公共
DIFY_BASE_URL=http://host.docker.internal:58080
DIFY_INTERNAL_KEY=LoginAuth2026          # Dify 回调后端内部 API（Header: X-Dify-Key）

# 打卡行为分析（开始节点 7 变量平铺，无需 DIFY_CHECKIN_INPUT_VAR）
DIFY_CHECKIN_API_KEY=app-kFnPhD8YUmCfG2DeHubp1Scc
DIFY_CHECKIN_RESPONSE_MODE=blocking

# 健康方案生成（开始节点 7 变量平铺，无需 DIFY_PLAN_INPUT_VAR）
DIFY_PLAN_API_KEY=app-fHORDdQjVq9GVtLcyD9WaL3g
DIFY_PLAN_RESPONSE_MODE=blocking

# 风险评估
DIFY_RISK_API_KEY=app-XuGJ0yp3XhejVrzE88dL1kHk
DIFY_RISK_INPUT_VAR=flat
DIFY_RISK_INPUT_FORMAT=object
DIFY_RISK_TRIGGER_MODE=api
DIFY_RISK_RESPONSE_MODE=blocking

# 资讯推荐
DIFY_ARTICLE_RECOMMEND_API_KEY=app-zBXmLq9LXjrF8UtuL9d8PhiS
DIFY_ARTICLE_RECOMMEND_INPUT_VAR=inputs
DIFY_ARTICLE_RECOMMEND_INPUT_FORMAT=object
DIFY_ARTICLE_RECOMMEND_RESPONSE_MODE=blocking
```

---

## 8. Schema 文件与契约类对照

| 工作流 | Schema 文件 | 契约类 |
|--------|-------------|--------|
| 打卡行为分析 | `backend/checkin-service/src/main/resources/dify/checkin-analysis-input.schema.json` | `DifyCheckinAnalysisWorkflowContract` |
| 健康方案生成 | 入参：`plan-generation-input.schema.json`<br>出参（LLM 浅层）：`plan-generation-llm-output.schema.json` | `DifyPlanWorkflowContract` / `DifyPlanLlmOutputAssembler` |
| 风险评估 | `backend/health-service/src/main/resources/dify/risk-assessment-input.schema.json` | `DifyRiskAssessmentWorkflowContract` |
| 资讯推荐 | `backend/article-service/src/main/resources/dify/article-recommend-input.schema.json` | `DifyArticleRecommendWorkflowContract` |

配置 Dify 工作流时，将对应 Schema 文件内容粘贴到开始节点的 `inputs` 变量 JSON Schema 配置中。

---

## 9. 未纳入本文档的 Dify 相关项

| 项目 | 说明 |
|------|------|
| 资讯初稿生成 (`DIFY_ARTICLE_DRAFT_*`) | 管理端前端占位，尚未由后端统一封装 Workflow 契约 |
| Dify 对话 / SSE 流式 | 方案生成对外 SSE 由 `plan-service` 拆分 blocking 响应推送，工作流本身使用 blocking 模式 |
