# Copilot Instructions

## 仓库结构

本仓库是一个 monorepo，包含两个独立子项目：

- **`dify/`** — Java Spring Boot CLI 应用，将 `.feature` 文件处理并上传到 Dify AI 知识库。
- **`code-qa-agent/`** — Python LangGraph ReAct Agent，提供代码库智能问答服务（Chainlit UI + MCP Server）。

---

## dify/ — Java 项目

### 构建与测试

```bash
# 构建
./gradlew assemble

# 通过 Gradle 运行，传入 源目录 和 目标目录
./gradlew bootRun --args='<源目录路径> <目标目录路径>'

# 构建可执行 jar
./gradlew bootJar

# 运行
java -jar build/libs/ai_for_test_charm-0.0.1-SNAPSHOT.jar <源目录路径> <目标目录路径>

# 运行所有 Cucumber 测试
TESTCHARM_DAL_DUMPINPUT=false ./gradlew cucumber

# 运行单个 feature 文件
TESTCHARM_DAL_DUMPINPUT=false ./gradlew cucumber -Pfile=src/test/resources/features/process_kb.feature
```

需要 Java 25 (adoptopenjdk-25.0.2+10.0.LTS) 。

### 架构

Spring Boot 命令行应用（`CommandLineRunner` + PicoCLI），**不是** Web 服务。处理流程：

1. **`KbProcessor`** — 遍历源目录的 `.feature` 文件，用 Gherkin 解析器提取场景/步骤/DocString/DataTable，格式化为纯文本写入目标目录。路径用 `-` 拼平（如 `folder/file.feature` → `folder-file.txt`）。
2. **`DifyKbUploader`** — 通过 `DifyApiClient`（Feign）将处理后的文件上传到 Dify AI 知识库。支持新建/更新文档、重试、上传完成标记 `_done.txt`。
3. **`--verify`** 模式对比本地场景数与 Dify 段落数，不匹配则抛异常。

CLI 选项：`--disable-upload`、`--upload-only`、`--verify`、`--retry-count`。

### 测试时的 Profile 切换

`spring.profiles.active=test` 时：
- `application.yml` 将 Dify API 端点指向 MockServer (`mock-server.tool.net:1080`)。
- `fore2e` 包（**位于 main 源码中**）提供 `@Primary` 的 `Waiting` bean，通过 MockServer 替代真实 sleep，使测试可验证等待行为。
- `TestLogAppender`（Logback appender）将 `DifyKbUploader` 日志转发到 MockServer，使测试可断言日志输出。

## 端到端测试框架，基于 JFactory，DAL-java

* 用到的重要测试框架都在这个开源代码仓中 https://github.com/leeonky/test-charm-java，主要是下面几个。需要时可以参考，从而更好地理解端到端测试
    * jfactory - 准备数据核心库
    * jfactory-cucumber - 桥接了 cucumber 和 jfactory
    * RESTful-cucumber - 发api请求，通过 DAL-java 来验证结果，也可以通过 jfactory 来准备请求数据
    * DAL-java - 验证结果核心库
    * DAL-extension-basic - 验证相关的各种扩展
    * DAL-extension-jfactory - 将 DAL-java 的语法与 jfactory 结合，可以更加灵活的准备数据
* 也可以通过mcp服务"Test-Charm-Question-and-Answer"来咨询有关这些测试框架的问题
* 端到端测试要点：
    * 用 JFactory 准备数据，会给字段提供默认值。当增加一个新字段时，原有的测试可能会因为新字段的默认值而失败，这种情况需要在测试中显式地为新字段赋值

### 测试数据流

1. **Spec 定义**（`org.testcharm.e2e.spec`）— JFactory Spec 使用中文类名，如 `FeatureFiles.Feature文件`、`Arguments.命令行参数`。
2. **DataRepository 路由**（`org.testcharm.e2e.Beans`）— `JFactory` 按类型分发：`FeatureFile` → 写入临时 `input/` 目录；`OutputFile` → 写入 `output/TestCharm/`；`HttpRequest` / `LoggingEvent` / `WaitingTime` → MockServer 录制。
3. **步骤执行**（`ProcessKbSteps`）— `@当` 步骤用 `java -jar` 启动被测 jar（`--spring.profiles.active=test`），传入临时目录路径。
4. **DAL 验证**（`@那么` 步骤）— 用 DAL-java 断言输出文件内容、退出码、MockServer 录制的请求和日志。

## 约定

- **中文 Gherkin**：Feature 文件使用 `# language: zh-CN`，关键字为 `功能`、`场景`、`假如`、`当`、`那么`。步骤定义注解使用
  `io.cucumber.java.zh_cn.*`。
- **中文 Spec 类名**：JFactory Spec 内部类使用中文名称，与 feature 文件中的字符串对应（如 `"Feature文件"` 映射到
  `FeatureFiles.Feature文件`）。
- **三引号转义**：Gherkin doc-string 中使用 `'''` 代替 `"""`，在 `ProcessKbSteps` 和 `FeatureFileDataRepository` 中运行时替换。
- **全面使用 Lombok**：所有 POJO 使用 `@Getter`/`@Setter`（或 `@Data`）。主代码和测试代码均配置了 Lombok 注解处理器。
- **Cucumber glue 包**：`org.testcharm` 和 `com.github` —— 运行 Cucumber 时必须同时指定（已在 `build.gradle` 和 `.run/`
  模板中配置）。
- **测试以独立进程运行被测应用**：Cucumber 步骤通过 `java -jar` 启动一个新 JVM 进程来运行应用，而非在同一 JVM 内调用。

---

## code-qa-agent/ — Python 项目

### 构建与测试

```bash
cd code-qa-agent

# 安装依赖（需要 Python 3.12+，见 .tool-versions: python 3.13.12）
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env   # 填入 CQA_LLM_API_KEY 等

# 运行 Chainlit 问答 UI
export CQA_WORKSPACE_PATH=/path/to/codebase
chainlit run app.py    # 浏览器访问 http://localhost:8000

# 运行 MCP Server（stdio / SSE / streamable-http）
python mcp_server.py
python mcp_server.py --transport streamable-http --port 3001

# Docker Compose 部署（同时启动 UI + MCP Server）
WORKSPACE_PATH=/path/to/codebase docker compose up --build

# 运行单元测试
cd tests && python -m pytest test_agent.py -v
# 或
python -m unittest tests/test_agent.py
```

### 架构

LangGraph ReAct Agent，通过 LangChain 工具（`list_directory`、`find_files`、`grep_code`、`read_file`、`get_symbols`、`get_repo_map`）主动探索代码库，再综合回答。

```
Chainlit UI ──→ CodeQAAgent.astream_response() ──→ LLM (OpenAI/Anthropic/Ollama)
                         │                                   │
                         └──── tool calls ──────────────────┘
                                    │
                              tools.py / repo_map.py
                              (read-only filesystem access)

MCP Server (mcp_server.py) ──→ CodeQAAgent.ask() [non-streaming]
```

双部署形态共享同一个 `CodeQAAgent` 类：
- **Chainlit** (`app.py`)：流式 UI，按 `thread_id` 维护多轮对话历史，使用 SQLite 持久化聊天记录。
- **MCP Server** (`mcp_server.py`)：无状态，每次调用创建新的 `CodeQAAgent` 实例，通过 `ask_repo_question` 工具暴露给其他 AI Agent。

### 关键约定

- **强制工具先行**：首轮对话使用 `tool_choice="required"/"any"`，确保 LLM 在回答前必须调用工具；调用工具后切换为 `tool_choice="auto"`。
- **自动检测并重试"规划式"回复**：`_looks_like_incomplete_response()` 检测 LLM 返回"我来查一下..."类的规划文本，触发重试并注入约束提示。
- **首轮注入目录树**：第一个问题自动调用 `list_directory` 并将结果注入 context，减少 LLM 盲目探索。
- **路径安全**：`_safe_path()` 防止路径遍历（`../` 攻击），所有工具都受 `CQA_WORKSPACE_PATH` 约束。
- **`get_repo_map`**：基于 tree-sitter AST 解析，生成全库符号索引（函数/类/方法签名），支持 20+ 语言，最多处理 200 个文件。
- **配置前缀**：所有环境变量以 `CQA_` 为前缀（见 `config.py`），使用 pydantic-settings 加载。

### e2e 测试与代码覆盖率

e2e 测试位于 `e2e-tests/`，是 Java Cucumber 项目，通过 HTTP/WebSocket 请求测试运行在 Docker 中的 Python 应用。

#### 运行 e2e 测试

```bash
cd e2e-tests

# 默认模型（mock-gpt），tool_choice=required
docker compose --profile default up -d
./gradlew cucumber -Ptags='not @deepseek-model'

# DeepSeek 模型（mock-deepseek-chat），tool_choice=auto
docker compose --profile default down
docker compose --profile deepseek up -d
./gradlew cucumber -Ptags='@deepseek-model'
```

`docker-compose.yml` 使用 **Docker Compose Profile** 切换被测模型：

```
┌─ profile: default ─────────────┬─ profile: deepseek ────────────────┐
│ code-qa-agent (18000:8000)      │ code-qa-agent-deepseek (18000)     │
│ CQA_LLM_MODEL: mock-gpt         │ CQA_LLM_MODEL: mock-deepseek-chat  │
│ tool_choice 首轮: required      │ tool_choice 首轮: auto             │
└─────────────────────────────────┴────────────────────────────────────┘
```

两服务共享端口，不可同时运行。`chat_api.feature` 中 `@deepseek-model` tag 标记需 deepseek profile 的场景。

#### 收集覆盖率

`app.py` 和 `mcp_server.py` 顶部有覆盖率引导代码，由 `COVERAGE_DATA_FILE` 环境变量控制。Docker Compose 启动时通过 `run-*-dev.sh` 脚本自动设置该变量，每次请求结束后自动保存 `.coverage-*` 数据文件到 `coverage-output/` 目录。

```bash
cd e2e-tests
./gradlew cucumber             # 先运行测试
./collect-coverage.sh          # 合并数据 + 输出报告
open coverage-output/html/index.html
```

#### 覆盖率机制

```
docker-compose.yml
  ├── init: true               # tini 作为 PID 1，正确转发 SIGTERM
  ├── COVERAGE_DATA_FILE env   # 通过 run-*-dev.sh 注入
  └── coverage-output/ volume  # 宿主机挂载，接收 .coverage-* 文件

app.py / mcp_server.py
  └── 顶部 coverage bootstrap  # 进程启动时自动启用 coverage
      ├── COVERAGE_DATA_FILE 非空时 import coverage + cov.start()
      ├── atexit 注册 save     # 正常退出时保存
      ├── SIGTERM/SIGINT 处理  # 信号终止时保存
      └── on_message 末尾 save # 每次请求后保存（保障实时写入）
```

#### 关键文件

| 文件 | 作用 |
|------|------|
| `.coveragerc` | coverage.py 配置（branch=True, source=/app, omit 排除项） |
| `e2e-tests/run-chainlit-dev.sh` | 注入 `COVERAGE_DATA_FILE` 环境变量 |
| `e2e-tests/run-mcp-dev.sh` | 同上 |
| `e2e-tests/docker-compose.yml` | `init: true` + volume 挂载 + profiles 切换模型 |
| `e2e-tests/bootstrap-python.sh` | 容器初始化（OS 依赖 + Python venv），含 apt-get 重试 |
| `e2e-tests/collect-coverage.sh` | 合并 `.coverage-*` 文件并生成 HTML 报告 |
| `app.py` + `mcp_server.py` | 顶部 coverage bootstrap + 请求末尾 save |
| `requirements.txt` | 含 `coverage>=7.0.0` |
