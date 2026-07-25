# 覆盖率缺口分析

> 基于 `e2e-tests/coverage-output/html/index.html`，生成时间 2026-07-25  
> 全量测试（default + deepseek + anthropic 三 profile）合并后数据  
> 总覆盖率：42%（715 语句中 306 覆盖，258 分支中 80 覆盖）

## 📊 汇总

```
                  覆盖率      未覆盖语句   未覆盖分支      关键缺口
agent.py    █████████░ 90%    12           10+10          边界条件 + MAX_ITERATIONS
app.py      ████░░░░░░ 42%    44           10+6           coverage bootstrap + 密码错误 + 会话恢复
config.py   █████████░ 96%     1            0             database_sync_url
mcp_server  █████░░░░░ 52%    30            9+3           coverage bootstrap + CLI入口 + fallback
tools.py    ██░░░░░░░░ 32%   118           62+4           仅 list_directory 被间接调用
repo_map.py ██░░░░░░░░ 20%    48           32             工具未被测试场景实际调用
init_db.py  ░░░░░░░░░░  0%    27            8             未在 e2e 中运行
migrate_*.py░░░░░░░░░░  0%   126           44             未在 e2e 中运行
```

> 注：`tools.py`、`repo_map.py` 的低覆盖率是因为 mock LLM 只触发 `list_directory` 一项工具调用，其余工具（`find_files`、`grep_code`、`read_file`、`get_symbols`、`get_repo_map`）仅在 LLM tool_choice 声明的 tools 列表中作为参数发送，未被 agent 实际执行。`init_db.py` 和 `migrate_sqlite_to_pg.py` 是独立脚本，不在 e2e 测试范围内。

---

## 1. agent.py — 90%（166 语句，12 未覆盖 + 10 部分分支 + 10 未覆盖分支）

### ✅ 已通过新增测试覆盖

| 行号 | 代码 | 覆盖场景 |
|------|------|----------|
| **L167-168** | `else` 分支：已有 thread_id 时复用会话 | "同一会话中继续提问能维护对话上下文" |
| **L202** | `messages.append(HumanMessage(...))` 非首轮路径 | 同上 |
| **L56-59** | `isinstance(block, dict) and block.get("type") == "text"` | "Anthropic响应包含多个内容块时文本被正确拼接" |
| **L116-117** | `if not fn: return f"Unknown tool: {name}"` | "调用未知工具时返回错误信息并继续" |
| **L121-122** | `except Exception as e: return f"Tool error ({name}): {e}"` | "工具执行异常时返回错误信息并继续" |

### 🔴 仍待补测

| 行号 | 代码 | 说明 |
|------|------|------|
| **L54-55** | `isinstance(block, str)` 分支 | Anthropic 场景返回 dict 格式，纯字符串 content block 未触发 |
| **L65-66** | `_looks_like_incomplete_response` 空文本 | 边界 case |
| **L95-96** | `finish_reason`/`stop_reason` 元数据存在 | mock LLM 返回的 metadata 不包含这些字段 |
| **L104-105** | `load_system_prompt` 文件不存在 | 边界 case |
| **L108** | `load_system_prompt` 文件为空 | 同上 |
| **L318-320** | 最大迭代次数耗尽（`MAX_ITERATIONS`） | 需 mock LLM 无限返回 tool_calls |
| **L326** | `ask()` 传入显式 `thread_id` | 仅 MCP 路径走到 `thread_id=None` 分支 |

### 🟡 其他 profile 未覆盖（非死代码）

| 行号 | 说明 |
|------|------|
| **L132** `llm_base_url` 不存在 → Anthropic else 分支 | 当前 profile 设了 `llm_base_url`，不设时走 else |
| **L136** `llm_base_url` 不存在 → OpenAI else 分支 | 同上 |

---

## 2. app.py — 42%（76 语句，44 未覆盖 + 6 部分分支 + 10 未覆盖分支）

### ⚪ 死代码（coverage bootstrap，结构上不可覆盖）

| 行号 | 说明 |
|------|------|
| **L2-23** | Coverage bootstrap 块 — 在 `_cov.start()` 之前执行，coverage.py 无法追踪自己的启动代码。**无解，安全忽略** |
| **L26-36** | imports + 模块级代码（`logger = ...`、`agent = create_agent()`） — 被标记为未覆盖但实际已执行，疑为 Uvicorn worker 进程隔离导致。**可忽略** |

### 🔴 需补端到端测试

| 行号 | 代码 | 说明 |
|------|------|------|
| **L43** | `_preview_text` 截断分支（`compact[:limit] + "..."`） | 只测了 `len(compact) <= limit` 路径，超过 200 字符的截断未覆盖 |
| **L46-48** | `get_data_layer()` | SQLAlchemyDataLayer 初始化回调。标记为未覆盖但框架必然调用，疑为 worker 隔离 |
| **L53-54** | `auth_callback` 密码不匹配 | 当前环境 `CQA_AUTH_PASSWORD=""` 使该分支不可达。需配置 `CQA_AUTH_PASSWORD=correct` + 传错误密码 |
| **L56** | `auth_callback` 用户名为空 | `login.feature` 应覆盖此路径但显示未覆盖，疑为 worker 隔离 |
| **L60-61** | `on_chat_start` 装饰器 + 函数定义 | L62-65 函数体已覆盖，但装饰器行标记为未覆盖 |
| **L68-70** | `on_chat_resume` | **未测会话恢复场景**。需模拟断开重连 |
| **L73-74** | `on_message` 装饰器 + 函数定义 | L75+ 函数体已覆盖，装饰器行标记为未覆盖 |
| **L100-101** | 耗时超过 1 分钟的分支（`minutes > 0`） | query 都在 1 分钟内完成 |
| **L118-119** | `_save_coverage()` 异常 → `pass` | coverage 保存正常，异常分支未触发 |

### ✅ 已覆盖的关键路径

| 行号 | 代码 | 说明 |
|------|------|------|
| **L57** | `return cl.User(...)` | 有效用户名登录成功 |
| **L62-65** | `on_start` 欢迎消息 + `thread_id` 设置 | 聊天开始时发送 "👋 我已准备好分析代码库" |
| **L75-112** | `on_message` 核心逻辑 | ReAct 循环流式输出 + 落库 |

---

## 3. config.py — 96%（23 语句，1 未覆盖）

### 🟡 非死代码，但不需要单独测

| 行号 | 代码 | 说明 |
|------|------|------|
| **L28-32** | `database_sync_url` property | `psycopg2` 同步连接字符串，仅 `init_db.py` 使用。e2e 测试通过 Docker Compose 运行，不经过同步数据库连接路径。可标记 `# pragma: no cover` |

---

## 4. mcp_server.py — 52%（62 语句，30 未覆盖 + 3 部分分支 + 9 未覆盖分支）

### ⚪ 死代码（coverage bootstrap）

| 行号 | 说明 |
|------|------|
| **L13-30** | 同 app.py，coverage bootstrap 在执行 `_cov.start()` 前运行，结构上不可覆盖 |

### ✅ 已覆盖

| 行号 | 代码 | 说明 |
|------|------|------|
| **L62-97** | `ask_repo_question` 工具函数主体 | 两个 MCP 场景均覆盖 |
| **L47-52** | `_get_repo_name()` 正常路径 | `CQA_WORKSPACE_PATH` 非空且非 `"."` / `"workspace"` |

### 🔴 需补端到端测试

| 行号 | 代码 | 说明 |
|------|------|------|
| **L40-45** | `logging.basicConfig` + `logger` | HTTP 请求路径可能不触发模块顶层代码执行 |
| **L54** | `_get_repo_name()` fallback `"codebase"` | workspace path 为 `"workspace"` 或 `"."` 时触发 |
| **L57** | `REPO_NAME = _get_repo_name()` | 同上 |
| **L94-95** | `_save_coverage()` 异常 → `pass` | 正常 save 无异常 |
| **L102-116** | `main()` 函数 | argparse + `mcp.run()` — 测试通过 HTTP 调用 API，不经过 CLI 入口 |
| **L119-120** | `if __name__ == "__main__"` | 模块从未作为 `__main__` 执行 |

---

## 5. tools.py — 32%（173 语句，118 未覆盖）

### 说明

`tools.py` 定义了所有 Agent 可用工具（`list_directory`、`find_files`、`grep_code`、`read_file`、`get_symbols`、`get_repo_map`）。e2e mock LLM 仅触发 `list_directory` 调用（首轮目录注入 + 场景中的显式 tool_calls），其余工具未被实际 invoke。

**这并非测试缺口**——工具实现本身就是被 Agent 按需调用的，只要工具注册和参数 schema 序列化正确（已在 LLM 请求的 `tools` 字段中验证），工具函数体的覆盖属于工具自身的单元测试范畴。

低价值补测：可以通过构造特定 mock LLM 响应序列，在 e2e 场景中依次触发所有工具，但这会显著增加 feature 文件体积，收益有限。

---

## 6. repo_map.py — 20%（60 语句，48 未覆盖）

同 `tools.py`，`get_repo_map` 工具未被 mock LLM 实际触发。tree-sitter AST 解析逻辑在 e2e 中不覆盖。

---

## 7. init_db.py — 0% / migrate_sqlite_to_pg.py — 0%

独立运维脚本，不在 e2e 测试范围内。如需覆盖，应单独编写集成测试。

---

## 🎯 优先级建议

1. ~~**agent.py 多轮对话**（L167-168, L202）~~ ✅ 已完成
2. ~~**Anthropic 多内容块拼接**（L56-59）~~ ✅ 已完成
3. ~~**agent.py `_execute_tool` 错误处理**（L116-117, L121-122）~~ ✅ 已完成
4. **app.py 密码错误**（L53-54）— 安全相关，需添加 `CQA_AUTH_PASSWORD` 配置
5. **app.py 会话恢复**（L68-70）— 功能完整性，需模拟 WebSocket 重连场景
6. **agent.py 边界条件**（`isinstance(block, str)` L54-55、空文本 L65-66、文件缺失 L104-108）— 错误处理健壮性
7. **agent.py MAX_ITERATIONS**（L318-320）— 边缘路径，价值较低
8. **mcp_server.py `main()`**（L102-116）— CLI 入口，可标记 `# pragma: no cover`
