# 🔍 Code Q&A Agent

基于 LLM Agent 的代码库智能问答服务。使用 **Agentic 方式**（主动搜索 + 阅读 + 推理）取代传统向量检索，实现对代码库的深度理解和问答。

## 架构

```
┌─ 用户 (浏览器) ──┐
│  Chainlit Chat UI │
└────────┬──────────┘
         │
┌────────▼──────────┐
│  LangGraph Agent  │
│  ┌──────────────┐ │
│  │  ReAct Loop  │ │
│  │  ┌────────┐  │ │
│  │  │  LLM   │◄─┼─┼── OpenAI / Anthropic / Ollama
│  │  └───┬────┘  │ │
│  │      │       │ │
│  │  ┌───▼────┐  │ │
│  │  │ Tools  │  │ │
│  │  │·grep   │  │ │
│  │  │·find   │  │ │
│  │  │·read   │  │ │
│  │  │·ls     │  │ │
│  │  └────────┘  │ │
│  └──────────────┘ │
└────────┬──────────┘
         │ (read-only)
┌────────▼──────────┐
│  Target Codebase  │
│  (volume mount)   │
└───────────────────┘
```

## 快速开始

### 1. 配置环境变量

```bash
cd code-qa-agent
cp .env.example .env
# 编辑 .env，填入你的 LLM API Key
```

### 2. Docker Compose 部署（推荐）

```bash
# WORKSPACE_PATH 指向你要分析的代码库
WORKSPACE_PATH=/path/to/your/codebase docker compose up --build
```

浏览器打开 http://localhost:8000

### 3. 本地直接运行

```bash
# 需要 Python 3.12+, ripgrep
pip install -r requirements.txt

# 设置要分析的代码库路径
export CQA_WORKSPACE_PATH=/path/to/your/codebase

chainlit run app.py
```

## 配置项

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `CQA_LLM_PROVIDER` | `openai` | LLM 提供商: `openai` \| `anthropic` |
| `CQA_LLM_MODEL` | `gpt-4o` | 模型名称 |
| `CQA_LLM_API_KEY` | — | API Key |
| `CQA_LLM_BASE_URL` | — | 自定义 API 端点 (Ollama/vLLM) |
| `CQA_WORKSPACE_PATH` | `/workspace` | 目标代码库路径 |

### 使用 Ollama 本地模型

```env
CQA_LLM_PROVIDER=openai
CQA_LLM_MODEL=qwen2.5-coder:32b
CQA_LLM_API_KEY=ollama
CQA_LLM_BASE_URL=http://host.docker.internal:11434/v1
```

## Agent 工具集

| 工具 | 功能 | 底层实现 |
|------|------|---------|
| `list_directory` | 查看目录树 | Python pathlib |
| `find_files` | 按模式查找文件 | Python glob |
| `grep_code` | 搜索代码内容 | ripgrep (rg) |
| `read_file` | 读取文件内容 | Python + 行号范围 |
| `get_repo_map` | 生成全库符号地图 | tree-sitter AST 解析 |
| `get_symbols` | 提取单文件符号 | tree-sitter AST 解析 |

`get_repo_map` 借鉴了 [Aider](https://github.com/Aider-AI/aider) 的 repo-map 思路，通过 tree-sitter 解析源码 AST，提取函数、类、方法签名，生成全库"目录索引"。支持 20+ 种语言（Python, Java, Go, Rust, TypeScript, C/C++, Kotlin 等）。

## 工作原理

与传统 RAG（Chunk → Embed → Vector Search → LLM）不同，本工具采用 **Agentic 方式**：

1. **理解问题** — LLM 分析用户提问，制定搜索策略
2. **主动导航** — 使用工具浏览目录、搜索代码、阅读文件
3. **多轮迭代** — 根据中间结果调整搜索方向，追踪调用链
4. **综合回答** — 基于实际阅读的代码，给出带引用的精确回答

这种方式在理解代码结构、追踪调用关系、解释设计决策等方面，显著优于纯向量检索。

## License

MIT
