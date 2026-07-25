# Chainlit 聊天 HTTP 接口测试设计

> 覆盖率数据基于 2026-07-25 全量测试运行。`agent.py` 90%（166 语句，12 未覆盖），`app.py` 42%（76 语句，44 未覆盖，含 coverage bootstrap 死代码）。

## 范围

覆盖 `code-qa-agent` 的 Chainlit Web 应用在浏览器端实际使用的 HTTP 接口链路：

`/login` → `/set-session-cookie` → `/ws/socket.io`（polling）→ `/project/threads`

重点验证：

1. 登录失败分支（`login.feature` 独立覆盖）。
2. 聊天消息 payload 非法分支。
3. 聊天成功分支，以及后端对 LLM 接口的多次调用行为。
4. Agent ReAct 循环中的重试路径（无工具调用重试、规划文本重试）。
5. Agent 连续多次工具调用路径。
6. 同一会话多轮对话维护上下文。
7. Anthropic 多内容块响应拼接。

## 被测模块分析

### `agent.py` 核心流程

```text
[astream_response()]
  └─ _get_messages(thread_id)
       ├─ thread_id 不在 conversations ──→ 新建 system prompt + 首轮注入目录树
       └─ thread_id 已在 conversations ──→ 复用会话，直接追加用户消息
  └─ ReAct 循环 (for iteration in range(settings.max_iterations))
       ├─ has_tool_results == False
       │   └─ llm_with_required_tool (tool_choice=required/auto/any)
       │       ├─ 有 tool_calls ──→ 执行工具 → 继续循环
       │       └─ 无 tool_calls ──→ 追加"请使用工具"提示 → 继续循环
       │
       ├─ has_tool_results == True
       │   └─ llm_with_tools (tool_choice=auto/null)
       │       ├─ 有 tool_calls ──→ 执行工具 → 继续循环
       │       ├─ 无 tool_calls + 规划文本 ──→ 追加"请给最终答案" → 继续
       │       └─ 无 tool_calls + 最终答案 ──→ yield → return
       │
       └─ 耗尽迭代 ──→ 警告 → return

[_response_text()]
  └─ for block in content:
       ├─ isinstance(block, str) ──→ chunks.append(block)     ← Anthropic 多内容块场景
       └─ isinstance(block, dict) + type=="text" ──→ chunks.append(text)
```

### `_required_tool_choice()` 逻辑

| provider | model 含 "deepseek" | 返回值 |
|----------|---------------------|--------|
| anthropic | N/A | "any" |
| openai | 否 | "required" |
| openai | 是 | "auto" |

> e2e 通过 Docker Compose Profile 覆盖所有三条分支：`default`（required）、`deepseek`（auto）、`anthropic`（any）。

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
| Anthropic 响应 `content` | 单 block；多 block | Anthropic 响应 content 数组可含多个 text block，需验证拼接逻辑。 |

### LLM mock 响应序列等价类

| 等价类 | 说明 | 驱动路径 |
| --- | --- | --- |
| 直接回答（无 tool_calls） | 首轮 LLM 不调用工具，直接返回文本 | 无工具重试路径 |
| tool_calls → 最终回答 | 标准成功路径 | 已有用例覆盖 |
| tool_calls → 规划文本 | LLM 有工具结果后返回"let me check..." | 规划文本重试路径 |
| tool_calls → tool_calls → 最终回答 | 连续多次工具调用 | 多轮工具调用路径 |
| 含 `finish_reason=tool_calls` | 标准工具调用响应 | 所有工具调用场景 |
| 含 `finish_reason=stop` | 标准文本响应 | 所有文本回答场景 |
| 同一 session 连续两次提问 | 第二次提问时已有会话历史 | 多轮对话路径 |
| 连续 tool_calls 耗尽迭代上限 | mock LLM 始终返回 tool_calls，直到到达 CQA_MAX_ITERATIONS | 最大迭代耗尽路径 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| 登录响应 | HTTP 状态码与 JSON `detail/success`。 |
| polling 响应 | `task_start`、`new_message`、`task_end` 等 socket.io 包。 |
| 线程持久化结果 | `/project/threads` 中线程、用户消息、助手消息、`on_message` 运行步骤。 |
| LLM 出站请求 | 调用次数，以及每轮 `tool_choice` 值（`required` / `null` / `auto` / `any`）。 |

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
                                              ├─ tool_calls → 最终回答 (已有用例)
                                              └─ 非首轮消息 ──→ 追加 HumanMessage(复用会话)
                                              ↓
                                      [助手消息落库]
                                              ↓
                                  [/project/threads 返回线程]

[Anthropic content 拼接]
  LLM 返回 content: [{type: "text", text: "前半"}, {type: "text", text: "后半"}]
       ↓
  _response_text() → isinstance(block, str)? No → isinstance(block, dict) + type=="text"?
       ├─ block.get("text") → "前半" → chunks.append
       └─ block.get("text") → "后半" → chunks.append
       ↓
  "".join(chunks) → "前半后半"
```

## 用例设计

| 用例名 | Profile | `login.username` | `message.id` | LLM mock 序列 | 期望 LLM 请求数 | 期望 tool_choice 序列 |
| --- | --- | --- | --- | --- | --- | --- |
| 用户名为空登录失败 | default | 空白字符串 | N/A | N/A | 0 | N/A |
| 非 UUID 消息 id 返回错误消息且不落库 | default | 非空字符串 | 非 UUID | 不需要 | 0 | N/A |
| 有效消息返回助手回复并落库 | default | 非空字符串 | UUID v4 | tool_calls → 最终回答 | 2 | required → null |
| 无工具调用时模型被要求重试 | default | 非空字符串 | UUID v4 | 直接回答(无tool_calls) → tool_calls → 最终回答 | 3 | required → required → null |
| 模型返回规划文本后触发重试 | default | 非空字符串 | UUID v4 | tool_calls → 规划文本(let me check) → 最终回答 | 3 | required → null → null |
| 模型连续多次工具调用 | default | 非空字符串 | UUID v4 | tool_calls(列出目录) → tool_calls(读取文件) → 最终回答 | 3 | required → null → null |
| 同一会话继续提问维护上下文 | default | 非空字符串 | UUID v4 | 首轮: tool_calls → 回答 · 次轮: 直接回答 | 3 | required → null → null |
| DeepSeek模型首轮tool_choice为auto | deepseek | 非空字符串 | UUID v4 | tool_calls → 最终回答 | 2 | auto → null |
| Anthropic提供者首轮tool_choice为any | anthropic | 非空字符串 | UUID v4 | tool_calls → 最终回答 | 2 | any → null |
| Anthropic多内容块文本拼接 | anthropic | 非空字符串 | UUID v4 | tool_calls → 多 text block 回答 | 2 | any → null |
| 调用未知工具时返回错误信息并继续 | default | 非空字符串 | UUID v4 | tool_calls(unknown) → 最终回答 | 2 | required → null |
| 工具执行异常时返回错误信息并继续 | default | 非空字符串 | UUID v4 | tool_calls(../etc) → 最终回答 | 2 | required → null |
| 达到最大迭代次数时返回警告 | default | 非空字符串 | UUID v4 | tool_calls × 3（不返回最终回答） | 3 | required → null → null |

## 覆盖性检查

1. 代码路径覆盖：
   - 登录失败路径（`login.feature` 覆盖）。 ✅
   - `client_message` 在 `message.id` 非法时的错误路径。 ✅
   - `client_message` 正常调用 agent 并落库的成功路径。 ✅
   - agent 首轮无 tool_calls 重试路径（`agent.py` ReAct 循环）。 ✅
   - agent 规划文本重试路径。 ✅
   - agent 连续多轮工具调用路径。 ✅
   - `_looks_like_incomplete_response()` 函数。 ✅
   - DeepSeek 模型 `tool_choice=auto` 路径。 ✅
   - Anthropic 提供者 `tool_choice=any` 路径。 ✅
   - 多轮对话复用会话（`agent.py:167-168` else 分支，`agent.py:202` 非首轮 HumanMessage）。 ✅ 新增
   - Anthropic 多内容块 `_response_text()` 路径（`agent.py:56-59` dict + type=="text" 分支）。 ✅ 新增
   - agent `_execute_tool` 未知工具名分支（`agent.py:116-117`）。 ✅ 新增
   - agent `_execute_tool` 工具执行异常分支（`agent.py:121-122`）。 ✅ 新增
   - agent 达到最大迭代次数路径（`agent.py:315-317`，通过 `CQA_MAX_ITERATIONS=3` 控制）。 ✅ 新增
2. 输入因子覆盖：
   - `login.username` 的空白/非空两类均覆盖。
   - `client_message.message.id` 的非法/合法两类均覆盖。
   - LLM mock 的所有等价类均覆盖。
   - Anthropic 响应的单 block / 多 block 均覆盖。
   - 非法工具名（`NonExistentTool`）等价类。 ✅ 新增
   - 工具参数触发运行时异常（path traversal）等价类。 ✅ 新增
3. 条件分支覆盖：
   - 登录回调 `username.strip()` 为假 / 为真。
   - `uuid.UUID(step_dict["id"]).version == 4` 为假 / 为真。
   - agent 首轮 `tool_choice=required` 与后续 `tool_choice=auto` 两个分支均覆盖。
   - `not tool_calls and not has_tool_results` 为真 / 为假。
   - `not tool_calls and has_tool_results and _looks_like_incomplete_response(...)` 为真 / 为假。
   - `tool_calls` 为真且循环继续的分支。
   - `thread_id not in conversations` 为真 / 为假（`agent.py:163-168`）。 ✅ 新增
   - `len(messages) == 1` 为真 / 为假（`agent.py:193-202`）。 ✅ 新增
   - `isinstance(block, str)` 为假 + dict `type=="text"` 分支（`agent.py:56-59`）。 ✅ 新增
   - `TOOLS_MAP.get(name)` 返回 `None` / 返回有效函数（`agent.py:115-117`）。 ✅ 新增
   - `fn.invoke(args)` 正常返回 / 抛异常（`agent.py:118-122`）。 ✅ 新增
   - `finish_reason`/`stop_reason` 元数据存在（`agent.py:94`）：在所有 mock 响应中为 `LlmResponse.Choice` 新增 `finishReason` 字段。 ✅ 新增
4. 已知缺口：
   - `load_system_prompt` 文件不存在/为空：已删除死代码，`system_prompt.md` 随仓库存在。
   - `_looks_like_incomplete_response` 空文本（`agent.py:65-66`）：边界 case。
   - `llm_base_url` 不存在时的 Anthropic/OpenAI 分支（`agent.py:130`, `agent.py:134`）：当前 profile 设了 base_url。
