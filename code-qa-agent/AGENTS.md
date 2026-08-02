# Copilot Instructions — code-qa-agent/

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
