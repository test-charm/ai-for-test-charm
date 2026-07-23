# Chainlit 聊天 HTTP 接口测试设计

## 范围

覆盖 `code-qa-agent` 的 Chainlit Web 应用在浏览器端实际使用的 HTTP 接口链路：

`/login` → `/set-session-cookie` → `/ws/socket.io`（polling）→ `/project/threads`

重点验证：

1. 登录失败分支。
2. 聊天消息 payload 非法分支。
3. 聊天成功分支，以及后端对 LLM 接口的多次调用行为。
4. Agent ReAct 循环中的重试路径（无工具调用重试、规划文本重试）。
5. Agent 连续多次工具调用路径。

## 被测模块分析

### `agent.py` 核心流程

```text
[astream_response()]
  └─ 首轮注入目录树 ──→ ReAct 循环
                          │
                          ├─ has_tool_results == False
                          │   └─ llm_with_required_tool (tool_choice=required)
                          │       ├─ 有 tool_calls ──→ 执行工具 → 继续循环
                          │       └─ 无 tool_calls ──→ 追加"请使用工具"提示 → 继续循环
                          │
                          ├─ has_tool_results == True
                          │   └─ llm_with_tools (tool_choice=auto/null)
                          │       ├─ 有 tool_calls ──→ 执行工具 → 继续循环
                          │       ├─ 无 tool_calls + 规划文本 ──→ 追加"请给最终答案" → 继续
                          │       └─ 无 tool_calls + 最终答案 ──→ yield → return
                          │
                          └─ 达到 MAX_ITERATIONS ──→ 警告 → return
```

### `_required_tool_choice()` 逻辑

| provider | model 含 "deepseek" | 返回值 |
|----------|---------------------|--------|
| anthropic | N/A | "any" |
| openai | 否 | "required" |
| openai | 是 | "auto" |

> e2e 通过 `@deepseek-model` tag + Docker Compose `deepseek` profile 覆盖 DeepSeek 路径。见场景「DeepSeek模型首轮tool_choice为auto」。

### `_looks_like_incomplete_response()` 检测关键词

- `let me also look`, `let me look`, `let me inspect`, `let me explore`, `let me check`, `let me search`
- `i'll look`, `i will look`, `i should look`
- `next, i'll`, `next, i will`

## 输入因子

| 因子 | 取值/等价类 | 说明 |
| --- | --- | --- |
| `login.username` | 空白字符串；非空字符串 | `app.py` 中空白用户名返回 `None`，非空用户名允许登录。 |
| `login.password` | 非空任意字符串 | 当前 e2e 环境 `CQA_AUTH_PASSWORD` 为空，后端不校验具体密码值，但表单字段必须存在。 |
| `session_id` | 有效 UUID v4 | 用于 `/set-session-cookie` 和 socket auth 的 `sessionId`。每个场景使用新的 UUID，避免内存会话串扰。 |
| `engine_sid` | 由 polling open 响应动态生成 | `/ws/socket.io` 首次 GET 的输出，后续 polling POST/GET 继续作为输入。 |
| `client_message.message.id` | 非 UUID；有效 UUID v4 | `chainlit.emitter.process_message()` 会强制要求 v4 UUID。 |
| `client_message.message.output` | 非空文本 | 首次交互时同时决定线程标题。 |
| LLM mock 响应序列 | 见等价类列表 | 模拟 LLM 返回不同响应序列，驱动 agent 不同代码路径。 |

### LLM mock 响应序列等价类

| 等价类 | 说明 | 驱动路径 |
| --- | --- | --- |
| 直接回答（无 tool_calls） | 首轮 LLM 不调用工具，直接返回文本 | 无工具重试路径 |
| tool_calls → 最终回答 | 标准成功路径 | 已有用例覆盖 |
| tool_calls → 规划文本 | LLM 有工具结果后返回"let me check..." | 规划文本重试路径 |
| tool_calls → tool_calls → 最终回答 | 连续多次工具调用 | 多轮工具调用路径 |
| 含 `finish_reason=tool_calls` | 标准工具调用响应 | 所有工具调用场景 |
| 含 `finish_reason=stop` | 标准文本响应 | 所有文本回答场景 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| 登录响应 | HTTP 状态码与 JSON `detail/success`。 |
| polling 响应 | `task_start`、`new_message`、`task_end` 等 socket.io 包。 |
| 线程持久化结果 | `/project/threads` 中线程、用户消息、助手消息、`on_message` 运行步骤。 |
| LLM 出站请求 | 调用次数，以及每轮 `tool_choice` 值（`required` / `null`）。 |

## 流程图

```text
[POST /login]
  ├─ username 空白 ──→ [401 credentialssignin]
  └─ username 非空 ──→ [200 success]
                        ↓
                 [POST /set-session-cookie]
                        ↓
                 [GET polling open]
                        ↓
                 [POST socket connect auth]
                        ↓
               [POST connection_successful]
                        ↓
               [POST client_message polling]
                  ├─ message.id 非 UUID ──→ [Error 消息] → [/project/threads 为空]
                  └─ message.id 为 UUID v4 ──→ [Agent ReAct 循环]
                                              ├─ 首轮无 tool_calls ──→ 重试 (tool_choice=required)
                                              ├─ tool_calls → 无 tool_calls + 规划 ──→ 重试
                                              ├─ tool_calls → tool_calls → 最终回答
                                              └─ tool_calls → 最终回答 (已有用例)
                                              ↓
                                      [助手消息落库]
                                              ↓
                                  [/project/threads 返回线程]
```

## 用例设计

| 用例名 | `login.username` | `message.id` | LLM mock 序列 | 期望 LLM 请求数 | 期望 tool_choice 序列 |
| --- | --- | --- | --- | --- | --- |
| 用户名为空登录失败 | 空白字符串 | N/A | N/A | 0 | N/A |
| 非 UUID 消息 id 返回错误消息且不落库 | 非空字符串 | 非 UUID | 不需要 | 0 | N/A |
| 有效消息返回助手回复并落库 | 非空字符串 | UUID v4 | tool_calls → 最终回答 | 2 | required → null |
| 无工具调用时模型被要求重试 | 非空字符串 | UUID v4 | 直接回答(无tool_calls) → tool_calls → 最终回答 | 3 | required → required → null |
| 模型返回规划文本后触发重试 | 非空字符串 | UUID v4 | tool_calls → 规划文本(let me check) → 最终回答 | 3 | required → null → null |
| 模型连续多次工具调用 | 非空字符串 | UUID v4 | tool_calls(列出目录) → tool_calls(读取文件) → 最终回答 | 3 | required → null → null |
| DeepSeek模型首轮tool_choice为auto | 非空字符串 | UUID v4 | tool_calls → 最终回答 | 2 | auto → null |

## 覆盖性检查

1. 代码路径覆盖：
   - 登录失败路径。 ✅
   - `client_message` 在 `message.id` 非法时的错误路径。 ✅
   - `client_message` 正常调用 agent 并落库的成功路径。 ✅
   - agent 首轮无 tool_calls 重试路径（`agent.py:247-263`）。 ✅ 新增
   - agent 规划文本重试路径（`agent.py:265-280`）。 ✅ 新增
   - agent 连续多轮工具调用路径（`agent.py:293-321`）。 ✅ 新增
   - `_looks_like_incomplete_response()` 函数。 ✅ 新增
   - DeepSeek 模型 `tool_choice=auto` 路径（`agent.py:35-36`）。 ✅ 新增
2. 输入因子覆盖：
   - `login.username` 的空白/非空两类均覆盖。
   - `client_message.message.id` 的非法/合法两类均覆盖。
   - LLM mock 的所有等价类均覆盖。
3. 条件分支覆盖：
   - 登录回调 `username.strip()` 为假 / 为真。
   - `uuid.UUID(step_dict["id"]).version == 4` 为假 / 为真。
   - agent 首轮 `tool_choice=required` 与后续 `tool_choice=auto` 两个分支均覆盖。
   - `not tool_calls and not has_tool_results` 为真 / 为假（新增）。
   - `not tool_calls and has_tool_results and _looks_like_incomplete_response(...)` 为真 / 为假（新增）。
   - `tool_calls` 为真且循环继续的分支（新增）。
4. 已知缺口：
   - `MAX_ITERATIONS` 达到上限路径（`agent.py:323`）：需 200 轮循环，不具实用性。
