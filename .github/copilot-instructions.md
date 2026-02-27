# Copilot Instructions

## 构建与测试

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

## 架构

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

## 开发过程

### 步骤

每次代码更改都在以下三个阶段之一。按步骤进行。运行任何测试之后，请展示测试运行结果。

### 新增测试

在此阶段的主要目标是根据用户需求，新增或修改测试代码，最终达到失败的测试信息足以判断代码行为**不符合**测试的要求。**这一步千万不要修改生产代码**，修改完测试之后可以直接运行测试，无需确认。

### 增加功能

在此阶段的主要目标是修改实现代码，使其通过新增的测试代码，并且通过所有现存测试，**请务必获取测试运行结果并确认其通过**。修改实现代码之后可以直接运行测试，并确认测试通过。**请务必只写最少的实现代来通过测试**

### 重构

在此阶段的主要目标是修改实现代码，按照用户提示调整设计。或是自动review，去掉重复代码和其他代码臭味。在这个过程中不应该破坏现有测试，修改任何代码之后请运行测试，并确认测试通过。
**重构完成后，请运行所有测试，不要只做构建**
