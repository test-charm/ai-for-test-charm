# Copilot Instructions — code-qa-agent/

## ⛔ TDD 阶段判定 — 每次代码改动前必须先输出当前阶段

新增测试 / 增加功能 / 重构 三选一。即使改动看起来是"基础设施代码"（数据库表、启动脚本、配置），
只要它支持新功能，就属于"新功能"，必须先进入「新增测试」阶段。

### 如何写新增测试

e2e 测试位于 `e2e-tests/src/test/resources/features/`，Java Cucumber + Mockserver 模拟 LLM API。

| 改动类型 | 测试文件 | 怎么做 |
|----------|---------|--------|
| MCP 工具行为变更（如新增持久化、修改回答格式） | `mcp_api.feature` | 新增场景，用 `当向MCP服务发送问题` + `那么MCP回答应为` + `并且数据应为` 验证 |
| MCP 新增数据库表写入 | `mcp_api.feature` | 新增场景验证 MCP 请求后对应表有新记录，可用 DAL `并且数据应为` 直接查表 |
| Chainlit UI 行为变更 | `chat_api.feature` | 新增场景验证 Socket.IO 事件流 |
| 工具函数变更 | `tools.feature` | 新增场景验证工具调用和 mock API 交互 |
| 启动脚本 / Docker 配置变更 | 现有 feature | 验证容器健康 + 功能正常即可，通常无需新增测试 |

### TDD 三步流程

1. **新增测试**：写测试，确认失败，**不要修改生产代码**
2. **增加功能**：修改实现代码，使新测试通过 + 所有现存测试通过
3. **重构**：消除重复/异味，不改变行为，运行全量测试确认通过

---

## 项目概述

Python LangGraph ReAct Agent，提供代码库智能问答服务（Chainlit UI + MCP Server）。

Python 3.12+。安装：`pip install -r requirements.txt`。配置：`cp .env.example .env`。

运行：
```bash
chainlit run app.py                           # UI → http://localhost:8000
python mcp_server.py                          # MCP stdio
python mcp_server.py --transport streamable-http --port 3001
```

## 架构

```
Chainlit UI ──→ CodeQAAgent.astream_response() ──→ LLM
                     │
                     └── tool calls ──→ tools.py / repo_map.py (read-only)

MCP Server ──→ CodeQAAgent.ask() [non-streaming]
```

- **Chainlit** (`app.py`)：流式 UI，按 `thread_id` 多轮对话，SQLAlchemyDataLayer 持久化。
- **MCP Server** (`mcp_server.py`)：无状态，每次新建 `CodeQAAgent`，暴露 `ask_repo_question` 工具。

## e2e 测试

位于 `e2e-tests/`，Java Cucumber 项目，通过 HTTP/WebSocket 测试 Docker 中的 Python 应用。使用 Docker Compose Profile 切换模型：

| Profile | 模型 | tool_choice |
|---------|------|-------------|
| `default` | mock-gpt | required |
| `deepseek` | mock-deepseek-chat | auto |
| `anthropic` | mock-claude | any |

```bash
cd e2e-tests
# 按 profile 启动 → 运行测试 → 切换前先 down 上一个
docker compose --profile default up -d --build --wait
./gradlew cucumber -Ptags='not @deepseek-model and not @anthropic-provider'
docker compose --profile default down
docker compose --profile deepseek up -d --build --wait
./gradlew cucumber -Ptags='@deepseek-model'
```

### 端到端测试框架，基于 JFactory，DAL-java

* 用到的重要测试框架都在这个开源代码仓中 https://github.com/leeonky/test-charm-java，主要是下面几个。需要时可以参考，从而更好地理解端到端测试
  * jfactory - 准备数据核心库
  * jfactory-cucumber - 桥接了 cucumber 和 jfactory
  * RESTful-cucumber - 发api请求，通过 DAL-java 来验证结果，也可以通过 jfactory 来准备请求数据
  * DAL-java - 验证结果核心库
  * DAL-extension-basic - 验证相关的各种扩展
  * DAL-extension-jfactory - 将 DAL-java 的语法与 jfactory 结合，可以更加灵活的准备数据
* 也可以通过mcp服务“test-charm”来咨询有关这些测试框架的问题
