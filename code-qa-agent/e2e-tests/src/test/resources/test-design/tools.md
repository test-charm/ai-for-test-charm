# tools.py 工具函数测试设计

> `tools.py` 提供 6 个 LangChain `@tool` 函数，供 Agent ReAct 循环调用。这些工具通过 Agent 间接执行，测试需通过 Chainlit Socket.IO 或 MCP HTTP 接口，由 Mock LLM 响应驱动 Agent 调用特定工具。

## 范围

覆盖 `code-qa-agent/tools.py` 中所有 6 个工具函数的核心代码路径：

1. `list_directory` — 目录树遍历
2. `find_files` — 文件 glob 查找
3. `grep_code` — 代码正则搜索
4. `read_file` — 文件内容读取
5. `get_symbols` — AST 符号提取
6. `get_repo_map` — 仓库符号索引

测试通过 Chainlit 聊天接口，Mock LLM 响应指定工具调用，验证：
- 工具实际执行结果（通过 LLM 请求中的 ToolMessage 内容验证）
- 错误处理路径（工具返回错误信息，Agent 不崩溃）
- 边界条件（空结果、不存在文件等）

## 被测模块分析

### `_safe_path(rel_path)` — 路径安全校验

```text
[_safe_path(rel_path)]
  ├─ workspace / rel_path 拼接后 resolve
  └─ 前缀不匹配 workspace ──→ ValueError("Path traversal blocked")
```

### `_should_ignore(path)` — 忽略目录过滤

```text
[_should_ignore(path)]
  └─ path.parts 中任一部分在 IGNORE_DIRS ──→ True
```

### `list_directory(path, max_depth)`

```text
[list_directory(path, max_depth)]
  ├─ _safe_path(path) ──→ ValueError
  ├─ target.is_dir() == False ──→ "Not a directory: {path}"
  └─ _walk 递归遍历
       ├─ depth > max_depth ──→ 提前返回
       ├─ PermissionError ──→ 静默跳过
       ├─ 过滤 IGNORE_DIRS 和 "." 开头的目录/文件
       ├─ lines > 500 ──→ 截断 + "... (N more entries truncated)"
       └─ 返回树形结构字符串
```

### `find_files(pattern, path)`

```text
[find_files(pattern, path)]
  ├─ _safe_path(path) ──→ ValueError
  ├─ target.glob(pattern) → 过滤 _should_ignore 和非文件
  ├─ matches 为空 ──→ "No files found matching: {pattern}"
  ├─ len(matches) >= 100 ──→ 截断 "... (limited to 100 results)"
  └─ 返回匹配文件列表
```

### `grep_code(pattern, file_glob, path)`

```text
[grep_code(pattern, file_glob, path)]
  ├─ _safe_path(path) ──→ ValueError
  ├─ rg 子进程
  │   ├─ returncode == 0 ──→ stdout (截断 8000 字符)
  │   ├─ returncode == 1 ──→ "No matches found."
  │   └─ returncode > 1 ──→ "Search error: ..."
  └─ TimeoutExpired ──→ "Search timed out (30s limit)."
```

### `read_file(file_path, start_line, end_line)`

```text
[read_file(file_path, start_line, end_line)]
  ├─ _safe_path(file_path) ──→ ValueError
  ├─ not exists ──→ "File not found: {file_path}"
  ├─ not is_file ──→ "Not a file: {file_path}"
  ├─ PermissionError ──→ "Permission denied: {file_path}"
  ├─ end_line is None ──→ start_line + max_file_lines - 1
  ├─ end_line < total ──→ 追加 "Use read_file(...) to continue"
  └─ 返回带行号的行内容
```

### `get_symbols(file_path)`

```text
[get_symbols(file_path)]
  ├─ _safe_path(file_path) ──→ ValueError
  ├─ not is_file ──→ "File not found: {file_path}"
  ├─ detect_language → None ──→ "Unsupported language for: {file_path}"
  ├─ extract_symbols → [] ──→ "No symbols extracted from {file_path}"
  └─ 返回符号列表
```

### `get_repo_map(path, file_glob)`

```text
[get_repo_map(path, file_glob)]
  ├─ _safe_path(path) ──→ ValueError
  ├─ target.glob(glob_pattern) → 遍历
  │   ├─ _should_ignore ──→ 跳过
  │   ├─ detect_language 不支持 ──→ 跳过
  │   ├─ extract_symbols → [] ──→ 跳过
  │   └─ file_count >= 200 ──→ 截断 "... (limited to 200 files)"
  ├─ result_lines 为空 ──→ "No parseable source files found."
  └─ 返回 header + 符号索引
```

## 输入因子

| 因子 | 工具 | 取值/等价类 | 说明 |
| --- | --- | --- | --- |
| `path` (list_directory) | list_directory | 有效目录；文件路径（非目录） | `_safe_path` 通过后检查 `is_dir()` |
| `max_depth` (list_directory) | list_directory | 不传(默认3)；传入显式值 | 影响树的深度，但都属成功等价类 |
| `pattern` (find_files) | find_files | 有匹配的 glob；无匹配的 glob | `**/*.py` 会匹配 Python 文件；`*.nonexistent` 不匹配 |
| `path` (find_files) | find_files | 有效目录 | |
| `pattern` (grep_code) | grep_code | 有匹配的正则；无匹配的正则 | `def ` 匹配函数定义；`ZZZ_NONEXISTENT` 无匹配 |
| `file_glob` (grep_code) | grep_code | 不传(None)；"*.py" | 控制搜索文件范围 |
| `file_path` (read_file) | read_file | 存在的文件；不存在的文件；目录路径 | |
| `start_line` (read_file) | read_file | 不传(默认1)；显式值 | |
| `end_line` (read_file) | read_file | 不传(None)；显式值 | |
| `file_path` (get_symbols) | get_symbols | 存在的 Java 文件；不支持类型的文件；不存在的文件 | |
| `file_glob` (get_repo_map) | get_repo_map | 不传(None)；"**/*.py" | 控制解析的文件范围 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| 工具返回字符串 | 每个工具的执行结果，包含在 ToolMessage 中发送给 LLM |
| Agent 最终回答 | Mock LLM 返回的预编程文本 |
| LLM 请求中的 ToolMessage | 工具执行后，Agent 发送至 LLM 的消息中包含 `role: tool` 的内容 |

## 流程图

```text
[用户发送消息]
       ↓
[POST /login] → [200 success]
       ↓
[POST /set-session-cookie]
       ↓
[Socket.IO 连接 + client_message]
       ↓
[Agent ReAct 循环]
       │
       ├── [首轮: llm_with_required_tool]
       │    ├─ Mock 返回 tool_calls → [执行工具]
       │    └─ 无 tool_calls → 重试
       │
       ├── [执行工具: _execute_tool(name, args)]
       │    ├─ TOOLS_MAP.get(name) == None → "Unknown tool: {name}"
       │    ├─ fn.invoke(args) 成功 → 返回工具结果
       │    └─ fn.invoke(args) 抛异常 → "Tool error ({name}): {e}"
       │
       └── [后续: llm_with_tools (tool_choice=null)]
            ├─ Mock 返回 tool_calls → [执行工具]
            ├─ Mock 返回规划文本 → 重试
            └─ Mock 返回最终回答 → yield → done
```

## 用例设计

### 用例矩阵

| 用例名 | 触发工具 | 工具参数 | LLM mock 序列 | 验证要点 |
| --- | --- | --- | --- | --- |
| list_directory对文件路径返回错误 | list_directory | `path="app.py"` | tool_calls(list_directory on app.py) → 最终回答 | LLM 请求中 ToolMessage 含 "Not a directory: app.py"，最终回答正常 |
| find_files无匹配时返回空结果 | find_files | `pattern="*.nonexistent"` | tool_calls(find_files *.nonexistent) → 最终回答 | LLM 请求中 ToolMessage 含 "No files found matching: *.nonexistent" |
| find_files正常匹配返回文件列表 | find_files | `pattern="build.gradle"` | tool_calls(find_files build.gradle) → 最终回答 | LLM 请求中 ToolMessage 含 "build.gradle"，覆盖正常匹配路径 |
| find_files结果超过100个时触发截断 | find_files | `pattern="**/*.java"` | tool_calls(find_files **/*.java) → 最终回答 | 工作区 757 个 .java 文件，触发 100 结果截断。LLM 请求中 ToolMessage 正则匹配 `.*\(limited to 100 results\)/` |
| grep_code无匹配时返回空结果 | grep_code | `pattern="ZZZ_NONEXISTENT_PATTERN"` | tool_calls(grep_code nonexistent pattern) → 最终回答 | LLM 请求中 ToolMessage 含 "No matches found." |
| read_file读取不存在文件返回错误 | read_file | `file_path="nonexistent.txt"` | tool_calls(read_file nonexistent) → 最终回答 | LLM 请求中 ToolMessage 含 "File not found: nonexistent.txt" |
| read_file读取目录路径返回错误 | read_file | `file_path="code-qa-agent"` | tool_calls(read_file directory) → 最终回答 | LLM 请求中 ToolMessage 含 "Not a file: code-qa-agent" |
| find_files过滤掉IGNORE_DIRS中的.git目录文件 | find_files | `pattern="**/HEAD"` | tool_calls(find_files **/HEAD) → 最终回答 | `.git/HEAD` 被 `_should_ignore` 过滤，ToolMessage 含 "No files found matching: **/HEAD" |
| get_symbols分析存在文件正常返回内容 🆕 | get_symbols | `file_path="jfactory/src/.../CollectorInDAL.java"` | tool_calls(get_symbols on CollectorInDAL.java) → 最终回答 | LLM 请求中 ToolMessage 含 `class CollectorInDAL (L15-84)`，验证符号提取正常路径 |
| get_symbols分析存在文件但类型不支持 🆕 | get_symbols | `file_path="build.gradle"` | tool_calls(get_symbols on build.gradle) → 最终回答 | LLM 请求中 ToolMessage 含 "Unsupported language for: build.gradle" |
| get_symbols分析不存在文件返回错误 | get_symbols | `file_path="nonexistent.py"` | tool_calls(get_symbols nonexistent) → 最终回答 | LLM 请求中 ToolMessage 含 "File not found: nonexistent.py" |
| get_repo_map带glob过滤只分析Python文件 | get_repo_map | `file_glob="**/*.py"` | tool_calls(get_repo_map with glob) → 最终回答 | LLM 请求中 ToolMessage 含 "No parseable source files found"（tree-sitter 已安装但工作区不含 Python 文件） |

### 用例详情

#### 1. list_directory 对文件路径返回错误

- **最短路径**：`_safe_path` 通过 → `target.is_dir()` 返回 False
- **输入**：`path="app.py"`（文件路径，非目录）
- **预期输出**：工具返回 "Not a directory: app.py"
- **Agent 行为**：正常完成循环，最终回答来自 Mock

#### 2. find_files 无匹配时返回空结果

- **最短路径**：glob 遍历无任何匹配
- **输入**：`pattern="*.nonexistent"`
- **预期输出**：工具返回 "No files found matching: *.nonexistent"
- **Agent 行为**：正常完成循环

#### 3. grep_code 无匹配时返回空结果

- **最短路径**：rg 进程 returncode == 1
- **输入**：`pattern="ZZZ_NONEXISTENT_PATTERN"`
- **预期输出**：工具返回 "No matches found."
- **Agent 行为**：正常完成循环

#### 4. read_file 读取不存在文件返回错误

- **最短路径**：`_safe_path` 通过 → `not exists` → 错误
- **输入**：`file_path="nonexistent.txt"`
- **预期输出**：工具返回 "File not found: nonexistent.txt"
- **Agent 行为**：正常完成循环

#### 5. read_file 读取目录路径返回错误

- **最短路径**：`_safe_path` 通过 → `not is_file` → 错误（先经过 exists 检查，目录存在但非文件）
- **输入**：`file_path="code-qa-agent"`（目录路径）
- **预期输出**：工具返回 "Not a file: code-qa-agent"
- **Agent 行为**：正常完成循环

#### 6. get_symbols 分析存在文件正常返回内容 🆕

- **最短路径**：`_safe_path` 通过 → `is_file()` True → `extract_symbols` 成功 → 返回符号列表
- **输入**：`file_path="jfactory/src/main/java/org/testcharm/extensions/dal/CollectorInDAL.java"`
- **预期输出**：工具返回 12 行符号信息，以 `class CollectorInDAL (L15-84)` 开头
- **Agent 行为**：正常完成循环
- **覆盖目标**：L188 `is_file()` True → L192 `extract_symbols` 调用 → L193 `not symbols` False → L199-205 符号拼接返回。这是 `get_symbols` 核心正常路径。

#### 7. get_symbols 分析存在文件但类型不支持 🆕

- **最短路径**：`_safe_path` 通过 → `is_file()` True → `extract_symbols` 返回 []（`detect_language` 不支持 `.gradle`）→ L195 `not lang` → 返回错误
- **输入**：`file_path="build.gradle"`（Gradle 文件，tree-sitter 不支持）
- **预期输出**：工具返回 "Unsupported language for: build.gradle"
- **Agent 行为**：正常完成循环
- **覆盖目标**：L194-196：`detect_language` 返回 None → `"Unsupported language"` 路径

#### 8. get_symbols 分析不存在文件返回错误

- **最短路径**：`_safe_path` 通过 → `not is_file` → 错误
- **输入**：`file_path="nonexistent.py"`
- **预期输出**：工具返回 "File not found: nonexistent.py"
- **Agent 行为**：正常完成循环

#### 7. find_files 过滤掉 IGNORE_DIRS 中的 .git 目录文件

- **最短路径**：glob 匹配 `.git/HEAD` → `_should_ignore` 返回 True → 跳过 → matches 为空
- **输入**：`pattern="**/HEAD"`
- **预期输出**：工具返回 "No files found matching: \*\*/HEAD"
- **Agent 行为**：正常完成循环
- **验证方式**：`.git/HEAD` 在工作区根目录真实存在。若 `_should_ignore` 失效，该文件会出现在结果中导致测试失败。返回 "No files found" 即证明过滤生效。

#### 7.1. find_files 正常匹配返回文件列表 🆕

- **最短路径**：`_safe_path` 通过 → glob 匹配 `build.gradle` → `_should_ignore` False → `is_file()` True → 追加到 matches → 返回单文件列表
- **输入**：`pattern="build.gradle"`
- **预期输出**：工具返回 "build.gradle"
- **Agent 行为**：正常完成循环
- **覆盖目标**：L90→L93（append）、L97 False 分支、L100（join）、L101 False（< 100 无截断）、L103（正常返回）。这是 find_files 核心正常匹配路径，之前只有"无匹配"和"过滤"两个边界场景。

#### 7.2. find_files 结果超过 100 个时触发截断 🆕

- **最短路径**：`_safe_path` 通过 → glob 匹配 `**/*.java`（757 文件）→ 追加到 matches → L94 `>= 100` → break → L101 `== 100` → 追加截断后缀
- **输入**：`pattern="**/*.java"`
- **预期输出**：工具返回 100 行文件路径 + `\n... (limited to 100 results)`
- **Agent 行为**：正常完成循环
- **验证方式**：DAL 正则匹配 `content = /.*\(limited to 100 results\)/`。DAL-java 的 RegexNode 使用 `Pattern.DOTALL` + `Matcher.matches()`，`.` 默认跨行匹配，全串必须匹配。此前 `agent.py:293` 硬编码 4000 字符截断导致 ToolMessage 被截断，现已改为可配置的 `max_tool_result_chars`（默认 500000，环境变量 `CQA_MAX_TOOL_RESULT_CHARS`），确保完整结果送达 LLM/MockServer。
- **覆盖目标**：L94-95（`>= 100` break）、L101-102（`== 100` 截断后缀）。至此 find_files 全部代码路径已覆盖。

#### 9. get_repo_map 带 glob 过滤

- **最短路径**：正常遍历，通过 glob 过滤文件
- **输入**：`file_glob="**/*.py"`
- **预期输出**：符号索引为空（工作区不含 Python 源文件，tree-sitter 已安装但无可解析文件），返回 "No parseable source files found"
- **Agent 行为**：正常完成循环

#### 10. get_repo_map 带 glob 过滤存在文件匹配触发 200 个文件数限制 🆕

- **最短路径**：`_safe_path` 通过 → glob 匹配 `**/*.java`（757 文件）→ `detect_language` 命中 Java → `extract_symbols` 提取符号 → `file_count >= 200` → break → 追加 `\n... (limited to 200 files)`
- **输入**：`file_glob="**/*.java"`
- **预期输出**：200 个文件的符号索引 + `\n... (limited to 200 files)`
- **Agent 行为**：正常完成循环，LLM 返回 "get_repo_map结果：符号索引完成。"
- **验证方式**：`content::should.endsWith` 验证 ToolMessage 以 `... (limited to 200 files)` 结尾。200 个 Java 文件的符号索引输出约 20 万字符，`max_tool_result_chars` 默认 500000 保证不被截断。
- **覆盖目标**：`repo_map.py` L246-248（`file_count >= 200` break + 截断后缀）。

## 覆盖性检查

### 1. 代码路径覆盖

| 路径 | 覆盖状态 |
| --- | --- |
| `list_directory` → target.is_dir() == False | ✅ 用例 1 |
| `find_files` → matches 为空 | ✅ 用例 2, 7 |
| `find_files` → 正常匹配返回文件列表 | ✅ 🆕 用例 7.1 |
| `find_files` → 100 结果截断 | ✅ 🆕 用例 7.2 |
| `grep_code` → rg returncode == 1 | ✅ 用例 3 |
| `read_file` → not exists | ✅ 用例 4 |
| `read_file` → not is_file (目录) | ✅ 用例 5 |
| `get_symbols` → not is_file | ✅ 用例 8 |
| `get_symbols` → 正常提取符号 🆕 | ✅ 用例 6 |
| `get_symbols` → detect_language 返回 None (不支持类型) 🆕 | ✅ 用例 7 |
| `get_repo_map` → file_glob 过滤 | ✅ 用例 9 |
| `_safe_path` → ValueError (path traversal) | ✅ 已有：chat_api.feature "工具执行异常" |
| `_execute_tool` → Unknown tool | ✅ 已有：chat_api.feature "调用未知工具" |
| `list_directory` → 正常目录树（首轮注入） | ✅ 已有：所有 chat_api 场景 |
| `read_file` → 正常读取 | ✅ 已有：chat_api.feature "模型连续多次工具调用" 的 read_file |
| `get_symbols` → 正常提取 | ✅ 已有：agent 内部首轮未直接测试，但可通过新用例 6 间接覆盖 |
| `get_repo_map` → 正常生成 | 未覆盖（可选路径，依赖 tree-sitter 安装状态） |
| `grep_code` → rg 未安装 fallback | 💀 已删除（死代码，容器必装 rg） |
| `grep_code` → timeout | 未覆盖（需构造大文件超时场景） |
| `grep_code` → returncode > 1 错误 | 🆕 用例：grep_code使用非法正则返回搜索错误 |
| `grep_code` → file_glob 传值 | 🆕 用例：grep_code使用file_glob过滤Python文件 |
| `grep_code` → 8000 字符截断 | 🆕 用例：grep_code大量匹配结果触发8000字符截断 |
| `list_directory` → 500 行截断 | 未覆盖（需构造大量文件场景） |
| `list_directory` → PermissionError | 未覆盖（需容器权限控制） |

### 2. 输入因子覆盖

| 因子 | 覆盖情况 |
| --- | --- |
| `list_directory.path`: 有效目录 | ✅ 已有 |
| `list_directory.path`: 文件路径 | ✅ 用例 1 |
| `list_directory.path`: path traversal | ✅ 已有 |
| `find_files.pattern`: 有匹配 | ✅ 已有（首轮目录树注入） |
| `find_files.pattern`: 有匹配 | ✅ 🆕 用例 7.1 |
| `find_files.pattern`: 无匹配 | ✅ 用例 2 |
| `grep_code.pattern`: 有匹配 | 未直接测试 |
| `grep_code.pattern`: 无匹配 | ✅ 用例 3 |
| `read_file.file_path`: 存在文件 | ✅ 已有 |
| `read_file.file_path`: 不存在文件 | ✅ 用例 4 |
| `read_file.file_path`: 目录路径 | ✅ 用例 5 |
| `get_symbols.file_path`: 存在文件 | ✅ 已有（间接） |
| `get_symbols.file_path`: 不存在文件 | ✅ 用例 6 |
| `get_repo_map.file_glob`: None | 已有（间接，首轮不调用） |
| `get_repo_map.file_glob`: "**/*.py" | ✅ 用例 7 |

### 3. 条件分支覆盖

| 条件 | 覆盖情况 |
| --- | --- |
| `_safe_path`: 前缀匹配/不匹配 | ✅ 已有 |
| `_should_ignore`: 匹配/不匹配 | ✅ 用例 7 / 已有 |
| `list_directory`: `target.is_dir()` 为 True/False | ✅ True: 已有; False: 用例 1 |
| `list_directory`: `depth > max_depth` | ✅ 已有（max_depth=1 深度限制） |
| `find_files`: `matches` 为空/非空 | ✅ 用例 2 / 🆕 用例 7.1 |
| `find_files`: `len(matches) >= 100` | ✅ 🆕 用例 7.2 |
| `find_files`: `len(matches) == 100` 截断后缀 | ✅ 🆕 用例 7.2 |
| `grep_code`: `result.returncode == 0/1/else` | ✅ 0: 间接; 1: 用例 3 |
| `grep_code`: `FileNotFoundError` / `TimeoutExpired` | 未覆盖 |
| `read_file`: `not exists` / `not is_file` / `PermissionError` | ✅ 用例 4, 5; PermissionError 未覆盖 |
| `read_file`: `end_line is None` / explicit | ✅ None: 已有; explicit: 未覆盖 |
| `read_file`: `end_line < total` | ✅ 已有 |
| `get_symbols`: `not is_file` / `lang is None` / `symbols 为空` | ✅ is_file: 用例 8; 🆕 lang None: 用例 7; 其他已有 |
| `get_repo_map`: `result_lines` 为空 | 未覆盖 |
| `get_repo_map`: `file_count >= 200` | ✅ 🆕 用例 10 |
| `get_repo_map`: `_should_ignore` / `detect_language` | ✅ 已有 |

### 4. 已知缺口

| 缺口 | 原因 |
| --- | --- |
| `grep_code` timeout 路径 | 需构造极大的工作区文件，不具实用性 |
| `list_directory` 500 行截断 | 需构造大量目录结构场景 |
| `read_file` PermissionError | 需特定文件权限设置 |
| ~~`get_repo_map` 200 文件截断~~ | ✅ 🆕 用例 10 已覆盖 |
| `get_repo_map` 无文件返回 | 已在用例 9 覆盖（工作区无 Python 文件，tree-sitter 已安装） |
| `get_symbols` extract_symbols 返回空（非"类型不支持"路径） | 需 tree-sitter 解析失败或文件无符号场景。用例 6 已验证正常提取路径，用例 7 已验证不支持类型路径，仅剩解析异常/空文件等极端路径未覆盖 |

## 实现说明

### 测试文件

- Feature 文件：`src/test/resources/features/tools.feature`
- 测试设计：`src/test/resources/test-design/tools.md`

### 验证策略

由于 `tools.py` 的工具是 LangChain `@tool` 装饰函数，通过 Agent ReAct 循环内部调用，测试采用以下方式：

1. **Mock LLM 驱动**：使用 `假如Mock API:` 步骤配置 MockServer，使 LLM 返回特定 `tool_calls`，触发 Agent 调用目标工具。
2. **Socket.IO 事件验证**：使用 `::eventually: { receivedEvents::filter: { name= new_message, data.type= assistant_message } }` 验证 Agent 成功生成了助手回复（确认工具执行后 Agent 未崩溃，继续正常工作）。
3. **工具执行确认**：Agent 日志 (`docker compose logs code-qa-agent`) 中可观察到工具实际执行的参数和返回结果。

### DAL 文本块比较限制

由于 DAL-java 的 `data.output= ```...``` ` 文本块比较在处理包含 `---`（三个连字符）的多行文本时存在匹配问题，本测试套件采用 `::filter` + `::eventually` 的方式确认 Agent 完成响应，而非精确匹配输出内容。

### 运行命令

```bash
# 启动默认 profile 服务
docker compose --profile default up -d

# 运行全部默认 profile 测试（含 tools.feature）
TESTCHARM_DAL_DUMPINPUT=false ./gradlew cucumber -Ptags='not @deepseek-model and not @anthropic-provider'

# 仅运行工具测试
TESTCHARM_DAL_DUMPINPUT=false ./gradlew cucumber -Pfile=src/test/resources/features/tools.feature
```
