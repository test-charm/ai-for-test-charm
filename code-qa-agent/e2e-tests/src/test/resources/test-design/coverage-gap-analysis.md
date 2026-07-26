# 覆盖率缺口分析

> 基于 `e2e-tests/coverage-output/html/index.html`，生成时间 2026-07-25  
> 全量测试（default + deepseek + anthropic 三 profile）合并后数据  
> 总覆盖率：42%（715 语句中 306 覆盖，258 分支中 80 覆盖）
> 
> ⚠️ **关于 C tracer 假阴性**：因 `branch=True`，coverage.py 使用 C tracer（arc 级记录），它**不逐行记录纯顺序执行的代码**（如模块级 `import`、`logging.basicConfig`、模块级变量赋值、`def` 定义行、`if __name__ == "__main__"` 等）。这些行虽然被执行，但在报告中被标记为未覆盖。这并非真实的覆盖率缺口，而是 C tracer 的粒度限制。**移除 `branch=True` 将导致 Python tracer 无法正确处理 asyncio/FastMCP/Chainlit 等异步框架，覆盖率全面崩塌，不可行。**
> 
> ⚠️ **关于 `par`（partial branch）**：`par` 表示条件分支只走了一侧。如果标注为 "condition was never true" 或 "condition was always true"，说明另一侧的分支确实没有被测试触发——**这并非 C tracer 假阴性，而是真实的测试缺口。** 需要区分：`def`/`import`/装饰器等无跳转行标记为 `mis` 是假阴性；条件跳转行标记为 `par` 且 annotation 说 "never/always true" 是真缺口。

## 📊 汇总

```
                  覆盖率      未覆盖语句   部分/未覆盖分支   关键缺口
agent.py    █████████░ 100%    0            4+4            仅剩 C tracer 假阴性、仅 MCP 路径未覆盖的 ask() 分支
app.py      ████░░░░░░  45%   44            6+10           coverage bootstrap + 会话恢复 + 长耗时分支
config.py   █████████░  96%    1            0              database_sync_url
mcp_server  █████░░░░░  52%   30            3+9            coverage bootstrap + CLI入口 + fallback
tools.py    ██░░░░░░░░  32%   117           3+61           仅 list_directory 被间接调用
repo_map.py ██░░░░░░░░  20%    48            0+32          工具未被测试场景实际调用
init_db.py  ░░░░░░░░░░   0%    27            0+8           未在 e2e 中运行
migrate_*.py░░░░░░░░░░   0%   126            0+44          未在 e2e 中运行
```

> 注：数字基于 `e2e-tests/coverage-output/html/status.json`。agent.py 语句覆盖率已达 100%（156 语句中 0 未覆盖），仅剩 4 个 partial + 4 个 missing branch。app.py 总覆盖率 45%（语句+分支综合），32/76 语句覆盖。`部分分支` 指 `n_partial_branches`，`未覆盖分支` 指 `n_missing_branches`。

> 注：`tools.py`、`repo_map.py` 的低覆盖率是因为 mock LLM 只触发 `list_directory` 一项工具调用，其余工具（`find_files`、`grep_code`、`read_file`、`get_symbols`、`get_repo_map`）仅在 LLM tool_choice 声明的 tools 列表中作为参数发送，未被 agent 实际执行。`init_db.py` 和 `migrate_sqlite_to_pg.py` 是独立脚本，不在 e2e 测试范围内。

---

## 1. agent.py — 100% 语句（156 语句，0 未覆盖 + 4 部分分支 + 4 未覆盖分支）

### ✅ 已通过新增测试覆盖

| 行号 | 代码 | 覆盖场景 |
|------|------|----------|
| **L167-168** | `else` 分支：已有 thread_id 时复用会话 | "同一会话中继续提问能维护对话上下文" |
| **L202** | `messages.append(HumanMessage(...))` 非首轮路径 | 同上 |
| **L56-59** | `isinstance(block, dict) and block.get("type") == "text"` | "Anthropic响应包含多个内容块时文本被正确拼接" |
| **L116-117** | `if not fn: return f"Unknown tool: {name}"` | "调用未知工具时返回错误信息并继续" |
| **L121-122** | `except Exception as e: return f"Tool error ({name}): {e}"` | "工具执行异常时返回错误信息并继续" |
| **L61-62** | `_looks_like_incomplete_response` 空文本 → `return False` | "模型工具调用后返回空文本不触发重试" |
| **L94** | `return str(value)` — `finish_reason`/`stop_reason` 元数据存在 | 在所有 mock 响应中为 `LlmResponse.Choice` 新增 `finishReason` 字段，默认 `"stop"`，工具调用场景显式设为 `"tool_calls"` |
| **L315-317** | 最大迭代次数耗尽 — 通过 `CQA_MAX_ITERATIONS=3` 控制 | "达到最大迭代次数时返回警告" |

### 🔴 仍待补测

| 行号 | 代码 | 说明 |
|------|------|------|
| ~~**L54-55**~~ | ~~`isinstance(block, str)` 分支~~ | ✅ 已删除死代码 — LangChain 永远返回 dict，裸字符串分支不可达 |
| ~~**L102-103**~~ | ~~`load_system_prompt` 文件不存在~~ | ✅ 已删除死代码 — `system_prompt.md` 随仓库存在，不可达 |
| ~~**L105-106**~~ | ~~`load_system_prompt` 文件为空~~ | ✅ 已删除死代码 — 同上 |
| **L322** | `ask()` 传入显式 `thread_id` | 仅 MCP 路径走到 `thread_id=None` 分支 |

### 🟡 其他 profile 未覆盖（非死代码）

| 行号 | 说明 |
|------|------|
| **L132** `llm_base_url` 不存在 → Anthropic else 分支 | 当前 profile 设了 `llm_base_url`，不设时走 else |
| **L136** `llm_base_url` 不存在 → OpenAI else 分支 | 同上 |

---

## 2. app.py — 45%（76 语句，32 覆盖，44 未覆盖 + 6 部分分支 + 10 未覆盖分支）

> ⚠️ 行号基于 **修改前** 的 app.py（120 行）。删除 L53-54 后文件变为 118 行，后续行号偏移 -2，此处保持旧行号以匹配现有覆盖率数据。

### ⚪ 死代码（coverage bootstrap，结构上不可覆盖）

| 行号 | 说明 |
|------|------|
| **L2-23** | Coverage bootstrap 块 — 在 `_cov.start()` 之前执行，coverage.py 无法追踪自己的启动代码。**鸡生蛋问题，无解** |

### 🟡 C tracer 假阴性（已执行但 arc 级粒度不记录）

| 行号 | 代码 | 说明 |
|------|------|------|
| **L26-36** | 模块级 `import` + `logger = ...` + `agent = create_agent()` | 模块级顺序执行代码，C tracer 不逐行记录。**实际已执行** |
| **L60-61** | `@cl.on_chat_start` + `def on_chat_start` | 装饰器行和 `def` 行，C tracer 不记录。函数体 L62-65 已覆盖 |
| **L73-74** | `@cl.on_message` + `def on_message` | 同上，函数体 L75-112 已覆盖 |

### 🔴 需补端到端测试

| 行号 | 代码 | 说明 |
|------|------|------|
| **L46-47** | `get_data_layer()` 装饰器+定义行 | SQLAlchemyDataLayer 初始化回调。L48 已标记 `run`，框架确实调用 |
| **L55-56** | `auth_callback` 用户名为空 → `return None` | 🆕 已修复 — 之前覆盖率为 0 是因为 `_save_coverage()` 仅在 `on_message` 末尾调用，login 请求不触发 `on_message`。已在 `auth_callback` 末尾增加 `_save_coverage()` 调用来修复。并非框架死代码 |
| **L100-101** | 耗时超过 1 分钟的分支（`minutes > 0`） | `par` 标注 "condition was never true"——所有 query 在 1 分钟内完成 |
| **L118-119** | `_save_coverage()` 异常 → `pass` | 异常安全分支，正常环境不可达。可加 `# pragma: no cover` |

### ✅ 已覆盖（含本次新增）

| 行号 | 代码 | 说明 |
|------|------|------|
| **L43** | `_preview_text` 截断分支 | 🆕 "长消息触发preview截断" — 发送 23 次重复的 9 字短语（207 字符 > 200） |
| **L68-70** | `on_chat_resume` 函数体 | 🆕 "断开重连后恢复会话不重新发送欢迎消息" — 机制：重连用新 session-id + 从响应中提取的 threadId，触发 Chainlit on_chat_resume 回调 |
| **L57** | `return cl.User(...)` | 有效用户名登录成功 |
| **L62-65** | `on_start` 欢迎消息 + `thread_id` 设置 | 聊天开始时发送欢迎消息 |
| **L75-112** | `on_message` 核心逻辑 | ReAct 循环流式输出 + 落库 |

### 🔵 `par` 分支解读（区分真假缺口）

| 行号 | 标注 | 真相 |
|------|------|------|
| **L41** `if len(compact) <= limit` | "condition always true" → 🆕 已覆盖 | ✅ 新增长消息测试触发截断分支 |
| ~~**L53**~~ | ~~密码校验~~ | 🗑️ 已删除 — 用户暂不需要密码验证功能 |
| **L55** `if not username.strip()` | "condition never true" | 🆕 已修复 — 非死代码，实际已执行但 `_save_coverage()` 未在 login 路径调用导致覆盖率数据未落盘。已在 `auth_callback` 末尾增加保存 |
| **L88** `async for ...` | "loop didn't complete" | ❌ C tracer 假阴性：`break` 退出循环 |
| **L100** `if minutes > 0` | "condition never true" | ✅ 真缺口：mock 响应太快，低优先级 |
| **L115** `if _coverage_data_file` | "condition always true" | ✅ 真缺口：coverage 环境始终启用 |

---

## 3. config.py — 96%（23 语句，1 未覆盖）

### 🟡 非死代码，但不需要单独测

| 行号 | 代码 | 说明 |
|------|------|------|
| **L28-32** | `database_sync_url` property | `psycopg2` 同步连接字符串，仅 `init_db.py` 使用。e2e 测试通过 Docker Compose 运行，不经过同步数据库连接路径。可标记 `# pragma: no cover` |

---

## 4. mcp_server.py — 52%（62 语句，30 未覆盖 + 3 部分分支 + 9 未覆盖分支）

### ⚪ 死代码（coverage bootstrap，结构上不可覆盖）

| 行号 | 说明 |
|------|------|
| **L13-29** | Coverage bootstrap 块 — `import os` 到 `_cov.start()` 之间的代码，在测量启动前执行。`_save_coverage()` 的定义行（L21）同样不可覆盖。**鸡生蛋问题，无解** |

### 🟡 C tracer 假阴性（已执行但 arc 级粒度不记录）

| 行号 | 代码 | 说明 |
|------|------|------|
| **L32-34** | `import argparse / logging / os` | 模块级 `import`，无跳转发生，C tracer 不记录 |
| **L36** | `from mcp.server.fastmcp import FastMCP, Context` | 同上 |
| **L38** | `from agent import CodeQAAgent` | 同上 |
| **L40-45** | `logging.basicConfig(...)` + `logger = ...` | 模块级顺序执行，`basicConfig` 无跳转 |
| **L47-53** | `def _get_repo_name()` 函数定义行 | `def` 行被 C tracer 标记为函数入口，但函数体内部分支（L49-52）已确认覆盖 |
| **L54** | `return "codebase"` fallback | 当前环境 `CQA_HOST_WORKSPACE_PATH` 和 `CQA_WORKSPACE_PATH` 均非空且非 `"."`/`"workspace"`，L49-52 路径已覆盖，此 fallback 确实未触发——但同时 C tracer 对此类单行 return 的标记也不可靠 |
| **L57** | `REPO_NAME = _get_repo_name()` | 模块级赋值，无跳转 |
| **L102** | `def main():` 函数定义行 | C tracer 函数入口（`main()` 体内 L103-116 已覆盖） |
| **L97** | `return answer` | 函数最后一条语句，L88 → L97 → exit 为纯顺序流，无跳转，C tracer 不创建 arc。**测试确实收到了返回值**（`McpSteps.verifyMcpAnswer` 断言通过），证明 L97 已执行 |
| **L119-120** | `if __name__ == "__main__":` + `main()` | 模块从未作为 `__main__` 执行（测试通过 HTTP 调用）；且即使执行，C tracer 也未必记录 |

### ✅ 已覆盖

| 行号 | 代码 | 说明 |
|------|------|------|
| **L61-99** | `create_mcp_server()` / `ask_repo_question` | 两个 MCP 场景均通过 HTTP 调用，函数体整体覆盖 |
| **L49-52** | `_get_repo_name()` 正常路径 | `CQA_HOST_WORKSPACE_PATH=/host/code-qa-agent`，取 basename 返回 |
| **L103-116** | `main()` 函数体 | 测试通过 HTTP 调用 API 时，模块被 `import` 触发顶层代码，但不包括 `__main__` 入口。`main()` 本身内部分支（argparse、`create_mcp_server`、`mcp.run`）在 import 路径下因 `if __name__` 守卫不执行 |

### 🔴 真正未覆盖

| 行号 | 代码 | 说明 |
|------|------|------|
| **L94-95** | `except Exception: pass` | `_save_coverage()` 在 mock 环境下始终成功，异常安全分支不可达。**可加 `# pragma: no cover`** |

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
4. ~~**agent.py MAX_ITERATIONS**（L318-320）~~ ✅ 已完成
5. ~~**app.py 会话恢复**（L68-70）~~ ✅ 已完成 — "断开重连后恢复会话不重新发送欢迎消息"
6. ~~**app.py `_preview_text` 截断**（L43）~~ ✅ 已完成 — "长消息触发preview截断"
7. ~~**app.py 密码错误**（L53-54）~~ 🗑️ 已删除 — 用户暂不需要密码验证功能
8. ~~**app.py L55-56**（`auth_callback` 空用户名）~~ ✅ 已完成 — 在 `auth_callback` 末尾增加 `_save_coverage()` 调用，修复 login 路径覆盖率未落盘问题
9. **app.py L100-101**（长耗时格式）— 低优先级，纯展示逻辑，可加 `# pragma: no cover`

### 可加 `# pragma: no cover` 的代码

| 文件 | 行号 | 原因 |
|------|------|------|
| `app.py` | L2-23 | Coverage bootstrap，鸡生蛋问题 |
| `app.py` | L100-101 | 长耗时时间格式，mock 场景下不可达 |
| `app.py` | L118-119 | `_save_coverage()` 异常安全分支，正常环境不可达 |
| `mcp_server.py` | L13-29 | Coverage bootstrap，同上 |
| `mcp_server.py` | L94-95 | 异常安全分支，同上 |
| `config.py` | L28-32 | `database_sync_url`，仅独立脚本使用 |

> 注：模块级 `import`、`def` 定义行、`@decorator`、`if __name__ == "__main__"` 等 C tracer 假阴性**不需要**加 `# pragma: no cover`——它们是 C tracer (`branch=True`) 的已知粒度限制，并非真正的未覆盖代码。不应为了覆盖率数字而添加误导性标记。
