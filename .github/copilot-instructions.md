# Copilot Instructions

## 仓库结构

本仓库是一个 monorepo，包含两个独立子项目：

- **`dify/`** — Java Spring Boot CLI 应用，将 `.feature` 文件处理并上传到 Dify AI 知识库。
- **`code-qa-agent/`** — Python LangGraph ReAct Agent，提供代码库智能问答服务（Chainlit UI + MCP Server）。

---

## dify/ — Java 项目

需要 Java 25。构建：`./gradlew assemble`，打包：`./gradlew bootJar`，测试：`TESTCHARM_DAL_DUMPINPUT=false ./gradlew cucumber`。

Spring Boot 命令行应用（`CommandLineRunner` + PicoCLI），**不是** Web 服务：
- **`KbProcessor`** — 解析 `.feature` 文件的场景/步骤/DocString/DataTable，格式化为纯文本写入目标目录（路径 `-` 拼平）。
- **`DifyKbUploader`** — 通过 Feign 上传到 Dify 知识库，支持重试。
- CLI 选项：`--disable-upload`、`--upload-only`、`--verify`、`--retry-count`。

测试 profile (`spring.profiles.active=test`) 将 API 指向 MockServer (`mock-server.tool.net:1080`)，`fore2e` 包提供 MockServer 替代 sleep 的 `Waiting` bean，`TestLogAppender` 将上传日志转发到 MockServer。

### 端到端测试框架（JFactory + DAL-java）

参考仓库：https://github.com/leeonky/test-charm-java（jfactory, jfactory-cucumber, RESTful-cucumber, DAL-java 等）。也可通过 MCP 服务 "Test-Charm-Question-and-Answer" 咨询。

数据流：JFactory Spec（中文类名）→ DataRepository 路由（按类型写文件/MockServer 录制）→ `@当` 步骤通过 `java -jar` 启动被测进程 → `@那么` 步骤用 DAL-java 断言。

约定：
- 中文 Gherkin (`# language: zh-CN`)，步骤注解用 `io.cucumber.java.zh_cn.*`
- JFactory Spec 内部类用中文名，Gherkin doc-string 用 `'''` 代替 `"""`
- 全面使用 Lombok，Cucumber glue 包：`org.testcharm` + `com.github`
- 测试以独立进程运行被测应用
- JFactory 新增字段会引入默认值，可能影响存量测试，需显式赋值

---

## code-qa-agent/ — Python 项目

Python 3.12+。安装：`pip install -r requirements.txt`。配置：`cp .env.example .env`。

运行：
```bash
chainlit run app.py                           # UI → http://localhost:8000
python mcp_server.py                          # MCP stdio
python mcp_server.py --transport streamable-http --port 3001
```

### e2e 测试

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
# 合并覆盖率：./collect-coverage.sh → open coverage-output/html/index.html
```

覆盖率通过 `COVERAGE_DATA_FILE` 环境变量控制，`run-*-dev.sh` 注入，每次请求后存 `.coverage-*` 到 `coverage-output/`。

### 架构

```
Chainlit UI ──→ CodeQAAgent.astream_response() ──→ LLM
                     │
                     └── tool calls ──→ tools.py / repo_map.py (read-only)

MCP Server ──→ CodeQAAgent.ask() [non-streaming]
```

- **Chainlit** (`app.py`)：流式 UI，按 `thread_id` 多轮对话，SQLAlchemyDataLayer 持久化。
- **MCP Server** (`mcp_server.py`)：无状态，每次新建 `CodeQAAgent`，暴露 `ask_repo_question` 工具。

### 关键约定

- **强制工具先行**：首轮 `tool_choice="required"/"any"`，之后 `"auto"`。
- **规划式回复重试**：`_looks_like_incomplete_response()` 检测到"我来查一下..."类输出时重试。
- **首轮注入目录树**：首个问题自动 `list_directory` 并注入 context。
- **路径安全**：`_safe_path()` 防 `../`，所有工具受 `CQA_WORKSPACE_PATH` 约束。
- **get_repo_map**：tree-sitter AST 全库符号索引，支持 20+ 语言，最多 200 文件。
- **配置前缀**：所有环境变量以 `CQA_` 为前缀（pydantic-settings）。
