# MCP Server 问答接口测试设计

> 覆盖率数据基于 2026-07-25 全量测试运行。`mcp_server.py` 52%（62 语句，30 未覆盖，含 coverage bootstrap 死代码），`agent.py` 90%。

## 范围

覆盖 `code-qa-agent` 的 MCP Server 通过 `ask_repo_question` 工具的问答流程：

MCP Client → HTTP(streamable-http) → `mcp_server.py` → `CodeQAAgent.ask()` → LLM → 响应

重点验证：

1. 标准问答路径：LLM 调用工具后给出最终回答。
2. 重试路径：LLM 首轮无工具调用后被要求重试。

## 被测模块分析

### `mcp_server.py` 核心流程

```text
[main() — L102-116]  (CLI 入口，测试不经过)
       ↓
[create_mcp_server() — L60]
       ↓
[FastMCP.mcp.tool("ask_repo_question") — L62]
       ↓
[ask_repo_question(question, ctx)]
  ├─ CodeQAAgent.ask(question)
  │    └─ astream_response() → ReAct 循环（同 chat_api）
  ├─ _save_coverage() [coverage 环境]
  └─ return answer

[_get_repo_name() — L47-54]
  ├─ CQA_HOST_WORKSPACE_PATH / CQA_WORKSPACE_PATH 存在且非 "." "workspace" "" → 返回目录名
  └─ 否则 → 返回 "codebase"
```

### MCP 与 Chainlit 的差异

| 维度 | Chainlit (`app.py`) | MCP Server (`mcp_server.py`) |
|------|---------------------|------------------------------|
| 调用入口 | `astream_response()` (流式) | `ask()` (非流式) |
| 会话模型 | 有状态，`conversations[thread_id]` 复用 | 每次调用新建 `CodeQAAgent`，无状态 |
| `thread_id` | 由 Chainlit session 提供 | `ask()` 自动生成 UUID（`thread_id=None` 分支） |
| 重试逻辑 | `tool_choice` 策略同 agent.py ReAct 循环 | 同，复用同一 `CodeQAAgent` 实例 |

## 输入因子

| 因子 | 取值/等价类 | 说明 |
| --- | --- | --- |
| `question` | 非空字符串 | MCP tool 参数，传给 `CodeQAAgent.ask()`。 |
| LLM mock 响应序列 | 见等价类列表 | 模拟 LLM 返回不同响应序列，驱动 agent 不同代码路径。 |
| `CQA_WORKSPACE_PATH` | 非空路径 | 控制 `_get_repo_name()` 的返回值。当前环境路径非 `"."` / `"workspace"`，故不触发 `"codebase"` fallback。 |

### LLM mock 响应序列等价类

| 等价类 | 说明 | 驱动路径 |
| --- | --- | --- |
| tool_calls → 最终回答 | 标准成功路径 | 已有用例覆盖 |
| 直接回答（无 tool_calls）→ 被要求重试 → tool_calls → 最终回答 | 首轮无工具调用重试 | 已有用例覆盖 |
| tool_calls → tool_calls → 最终回答 | 连续多次工具调用 | 复用 chat_api 路径验证 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| MCP 回答 | `ask_repo_question` 工具返回的最终文本。 |
| LLM 出站请求 | 调用次数、`tool_choice` 值。 |
| 工具调用记录 | `list_directory` 等工具的实际调用（在 container 日志中可观测）。 |

## 流程图

```text
[MCP Client]
     │
     ▼
[FastMCP HTTP endpoint]
     │
     ▼
[ask_repo_question(question)]
     │
     ▼
[CodeQAAgent(workspace=os.environ["CQA_WORKSPACE_PATH"])]
     │
     ▼
[agent.ask(question)]
     ├─ thread_id=None → uuid4()
     └─ astream_response(question, thread_id)
          │
          ▼
     [ReAct 循环]
          ├─ 首轮无 tool_calls ──→ 重试
          └─ tool_calls → 最终回答
          │
          ▼
     [yield ("token", ..., None)]
          │
          ▼
     [ask() 拼接 answer]
          │
          ▼
     [return answer]
```

## 用例设计

| 用例名 | question | LLM mock 序列 | 期望回答 | 期望 LLM 请求数 |
| --- | --- | --- | --- | --- |
| 通过MCP工具获取代码库回答 | "what is the entry point" | tool_calls → 最终回答 | "这是通过MCP工具返回的回答。代码入口在app.py中。" | 2 |
| LLM先无工具调用后被要求重试 | "retry question" | 直接回答(无tool_calls) → tool_calls → 最终回答 | "重试后返回的回答。" | 3 |

## 覆盖性检查

1. 代码路径覆盖：
   - `ask_repo_question` 标准问答路径。 ✅
   - `ask_repo_question` 首轮无工具调用重试路径。 ✅
   - `CodeQAAgent.ask()` 非流式集成。 ✅
2. 输入因子覆盖：
   - `question` 非空字符串。 ✅
   - LLM mock 的两个主要等价类均覆盖。 ✅
3. 条件分支覆盖：
   - `agent.ask()` 中 `thread_id is None` → 自动生成 UUID（`agent.py:324-325`，MCP 路径始终为真）。 ✅
   - agent ReAct 循环中 `has_tool_results == False` + 无 `tool_calls` → 重试。 ✅
4. 已知缺口：
   - `_get_repo_name()` 返回 `"codebase"` 的 fallback 路径（`mcp_server.py:54`）：当前 `CQA_WORKSPACE_PATH` 路径非空且非 `"."` / `"workspace"`，该分支不可达。
   - `main()` CLI 入口（`mcp_server.py:102-116`）：测试通过 HTTP 调用，不经过 argparse + `mcp.run()` 路径。
   - `logging.basicConfig` 模块级初始化（`mcp_server.py:40-45`）：HTTP 请求路径可能不触发模块顶层代码执行。
   - `_save_coverage()` 异常分支（`mcp_server.py:94-95`）：正常 save 无异常。
   - MCP 多轮对话（无状态 → 每次新建 agent，不存在会话复用问题）。
   - `MAX_ITERATIONS` 达到上限：同 chat_api，不具实用性。
