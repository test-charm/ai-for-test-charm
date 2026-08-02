# Copilot Instructions — dify/

## 项目概述

Java Spring Boot CLI 应用，将 `.feature` 文件处理并上传到 Dify AI 知识库。

需要 Java 25。构建：`./gradlew assemble`，打包：`./gradlew bootJar`，测试：`TESTCHARM_DAL_DUMPINPUT=false ./gradlew cucumber`。

Spring Boot 命令行应用（`CommandLineRunner` + PicoCLI），**不是** Web 服务：
- **`KbProcessor`** — 解析 `.feature` 文件的场景/步骤/DocString/DataTable，格式化为纯文本写入目标目录（路径 `-` 拼平）。
- **`DifyKbUploader`** — 通过 Feign 上传到 Dify 知识库，支持重试。
- CLI 选项：`--disable-upload`、`--upload-only`、`--verify`、`--retry-count`。

测试 profile (`spring.profiles.active=test`) 将 API 指向 MockServer (`mock-server.tool.net:1080`)，`fore2e` 包提供 MockServer 替代 sleep 的 `Waiting` bean，`TestLogAppender` 将上传日志转发到 MockServer。

## 端到端测试框架（JFactory + DAL-java）

参考仓库：https://github.com/leeonky/test-charm-java（jfactory, jfactory-cucumber, RESTful-cucumber, DAL-java 等）。也可通过 MCP 服务 "Test-Charm-Question-and-Answer" 咨询。

数据流：JFactory Spec（中文类名）→ DataRepository 路由（按类型写文件/MockServer 录制）→ `@当` 步骤通过 `java -jar` 启动被测进程 → `@那么` 步骤用 DAL-java 断言。

约定：
- 中文 Gherkin (`# language: zh-CN`)，步骤注解用 `io.cucumber.java.zh_cn.*`
- JFactory Spec 内部类用中文名，Gherkin doc-string 用 `'''` 代替 `"""`
- 全面使用 Lombok，Cucumber glue 包：`org.testcharm` + `com.github`
- 测试以独立进程运行被测应用
- JFactory 新增字段会引入默认值，可能影响存量测试，需显式赋值
