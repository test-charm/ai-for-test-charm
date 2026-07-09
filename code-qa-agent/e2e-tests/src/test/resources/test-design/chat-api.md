# Chainlit 聊天 HTTP 接口测试设计

## 范围

覆盖 `code-qa-agent` 的 Chainlit Web 应用在浏览器端实际使用的 HTTP 接口链路：

`/login` → `/set-session-cookie` → `/ws/socket.io`（polling）→ `/project/threads`

重点验证：

1. 登录失败分支。
2. 聊天消息 payload 非法分支。
3. 聊天成功分支，以及后端对 LLM 接口的两次调用行为。

## 输入因子

| 因子 | 取值/等价类 | 说明 |
| --- | --- | --- |
| `login.username` | 空白字符串；非空字符串 | `app.py` 中空白用户名返回 `None`，非空用户名允许登录。 |
| `login.password` | 非空任意字符串 | 当前 e2e 环境 `CQA_AUTH_PASSWORD` 为空，后端不校验具体密码值，但表单字段必须存在。 |
| `session_id` | 有效 UUID v4 | 用于 `/set-session-cookie` 和 socket auth 的 `sessionId`。每个场景使用新的 UUID，避免内存会话串扰。 |
| `engine_sid` | 由 polling open 响应动态生成 | `/ws/socket.io` 首次 GET 的输出，后续 polling POST/GET 继续作为输入。 |
| `client_message.message.id` | 非 UUID；有效 UUID v4 | `chainlit.emitter.process_message()` 会强制要求 v4 UUID。 |
| `client_message.message.output` | 非空文本 | 首次交互时同时决定线程标题。 |
| LLM mock 响应序列 | 缺省；两段固定响应 | 成功路径需要先返回 `tool_calls`，再返回最终回答。 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| 登录响应 | HTTP 状态码与 JSON `detail/success`。 |
| polling 响应 | `task_start`、`new_message`、`task_end` 等 socket.io 包。 |
| 线程持久化结果 | `/project/threads` 中线程、用户消息、助手消息、`on_message` 运行步骤。 |
| LLM 出站请求 | 调用次数，以及 `tool_choice=required/auto` 两个分支。 |

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
                  └─ message.id 为 UUID v4 ──→ [调用 LLM 两次]
                                              ↓
                                      [助手消息落库]
                                              ↓
                                  [/project/threads 返回线程]
```

## 用例设计

| 用例名 | `login.username` | `message.id` | LLM mock | 期望输出 |
| --- | --- | --- | --- | --- |
| 用户名为空登录失败 | 空白字符串 | N/A | N/A | `/login` 返回 `401` 与 `credentialssignin`。 |
| 非 UUID 消息 id 返回错误消息且不落库 | 非空字符串 | 非 UUID | 不需要 | polling 返回 `Error` 消息，`/project/threads` 返回空数组。 |
| 有效消息返回助手回复并落库 | 非空字符串 | UUID v4 | 第 1 次 `tool_calls`，第 2 次最终回答 | 线程保存问答内容，MockServer 记录 2 次 LLM 请求，分别命中 `required` 与 `auto`。 |

## 覆盖性检查

1. 代码路径覆盖：
   - 登录失败路径。
   - `client_message` 在 `message.id` 非法时的错误路径。
   - `client_message` 正常调用 agent 并落库的成功路径。
2. 输入因子覆盖：
   - `login.username` 的空白/非空两类均覆盖。
   - `client_message.message.id` 的非法/合法两类均覆盖。
   - LLM mock 的成功双响应路径覆盖。
3. 条件分支覆盖：
   - 登录回调 `username.strip()` 为假 / 为真。
   - `uuid.UUID(step_dict["id"]).version == 4` 为假 / 为真。
   - agent 首轮 `tool_choice=required` 与后续 `tool_choice=auto` 两个分支均覆盖。
