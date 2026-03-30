import json
import logging
import re
from typing import Any

from langchain_core.messages import SystemMessage, HumanMessage, AIMessage
from langchain_openai import ChatOpenAI
from langchain_anthropic import ChatAnthropic

from config import settings
from tools import list_directory, find_files, grep_code, read_file, get_symbols, get_repo_map

logger = logging.getLogger(__name__)

# Registry: name → callable
TOOLS_MAP: dict[str, Any] = {
    "list_directory": list_directory,
    "find_files": find_files,
    "grep_code": grep_code,
    "read_file": read_file,
    "get_symbols": get_symbols,
    "get_repo_map": get_repo_map,
}

SYSTEM_PROMPT = """\
You are a code analyst assistant. You explore codebases to answer questions.
Respond in the same language the user uses.
Be concise — give direct answers with code references. No filler or preamble.
"""

# Injected as first user message + assistant reply to establish the pattern
TOOL_INSTRUCTION = """\
You have access to these tools to explore the codebase. \
To use a tool, output a fenced JSON block exactly like this:

```json
{"tool": "list_directory", "args": {"path": "."}}
```

Available tools:
- list_directory(path=".", max_depth=3) — View directory tree
- find_files(pattern, path=".") — Find files by glob (e.g. "**/*.py")
- grep_code(pattern, file_glob=None, path=".") — Regex search in code
- read_file(file_path, start_line=1, end_line=None) — Read file lines
- get_repo_map(path=".", file_glob=None) — Symbol map via tree-sitter
- get_symbols(file_path) — Extract symbols from one file

Rules:
1. ALWAYS use tools via ```json blocks. NEVER just describe what you would do.
2. When you have enough info, give your final answer as plain text (no json blocks).
3. Cite file paths and line numbers.
"""

TOOL_INSTRUCTION_REPLY = """\
Understood. I will use ```json tool blocks to explore the codebase. Let me start.

```json
{"tool": "list_directory", "args": {"path": "."}}
```"""

MAX_ITERATIONS = 100

_TOOL_CALL_RE = re.compile(
    r"```json\s*\n(\{[^`]*?\})\s*\n```",
    re.DOTALL,
)


def _parse_tool_calls(text: str) -> list[dict]:
    """Extract tool call JSON blocks from LLM output."""
    calls = []
    for m in _TOOL_CALL_RE.finditer(text):
        try:
            obj = json.loads(m.group(1))
            if "tool" in obj:
                calls.append(obj)
        except json.JSONDecodeError:
            continue
    return calls


def _execute_tool(name: str, args: dict) -> str:
    """Execute a tool by name and return its result."""
    fn = TOOLS_MAP.get(name)
    if not fn:
        return f"Unknown tool: {name}"
    try:
        result = fn.invoke(args)
        return str(result)
    except Exception as e:
        return f"Tool error ({name}): {e}"


def _create_llm():
    if settings.llm_provider == "anthropic":
        return ChatAnthropic(
            model=settings.llm_model,
            api_key=settings.llm_api_key,
            max_tokens=8192,
        )
    kwargs = dict(model=settings.llm_model, api_key=settings.llm_api_key)
    if settings.llm_base_url:
        kwargs["base_url"] = settings.llm_base_url
    return ChatOpenAI(**kwargs)


class CodeQAAgent:
    """Text-based ReAct agent that works with ANY OpenAI-compatible API."""

    def __init__(self):
        self.llm = _create_llm()
        self.conversations: dict[str, list] = {}

    def _get_messages(self, thread_id: str) -> list:
        if thread_id not in self.conversations:
            # Few-shot: system prompt + injected user/assistant exchange
            # to firmly establish the tool-calling JSON format
            self.conversations[thread_id] = [
                SystemMessage(content=SYSTEM_PROMPT),
                HumanMessage(content=TOOL_INSTRUCTION),
                AIMessage(content=TOOL_INSTRUCTION_REPLY),
                HumanMessage(content="[Tool Results]\n\n### list_directory\n./\n├── src/\n│   ├── main.py\n│   └── utils.py\n└── README.md"),
                AIMessage(content='The project has a simple structure. Let me look deeper.\n\n```json\n{"tool": "read_file", "args": {"file_path": "src/main.py"}}\n```'),
                HumanMessage(content="[Tool Results]\n\n### read_file\n── src/main.py (1-10 of 10 lines) ──\n   1 │ print('hello')"),
                AIMessage(content="This is a simple Python project with a `main.py` entry point that prints 'hello'. The `utils.py` likely contains helper functions."),
            ]
        return self.conversations[thread_id]

    async def astream_response(self, user_input: str, thread_id: str):
        """Run the ReAct loop, yielding events for the UI layer.

        Event types:
            ("tool_start", tool_name, tool_args_str)
            ("tool_end", tool_name, result_str)
            ("token", token_str)
            ("done", None, None)
        """
        messages = self._get_messages(thread_id)

        # On first real question, pre-populate with actual project structure
        if len(messages) == 7:  # exactly the few-shot messages
            tree_result = _execute_tool("list_directory", {"path": ".", "max_depth": 2})
            messages.append(HumanMessage(content=f"[New conversation] Here is the project structure:\n{tree_result}\n\nNow answer my question: {user_input}"))
        else:
            messages.append(HumanMessage(content=user_input))

        for iteration in range(MAX_ITERATIONS):
            logger.info(f"Agent iteration {iteration + 1}/{MAX_ITERATIONS}")

            full_response = ""
            async for chunk in self.llm.astream(messages):
                if hasattr(chunk, "content") and chunk.content:
                    full_response += chunk.content

            logger.info(f"LLM response ({len(full_response)} chars): {full_response[:500]}")
            messages.append(AIMessage(content=full_response))

            tool_calls = _parse_tool_calls(full_response)
            logger.info(f"Parsed tool calls: {len(tool_calls)}")

            if not tool_calls:
                # No tool calls → this is the final answer, stream it
                for token in full_response:
                    yield ("token", token, None)
                yield ("done", None, None)
                return

            # Execute tools and build observation
            observations: list[str] = []
            for tc in tool_calls:
                tool_name = tc["tool"]
                tool_args = tc.get("args", {})
                yield ("tool_start", tool_name, json.dumps(tool_args, ensure_ascii=False)[:500])

                result = _execute_tool(tool_name, tool_args)
                truncated = result[:4000] if len(result) > 4000 else result
                observations.append(f"### {tool_name}\n{truncated}")
                yield ("tool_end", tool_name, truncated[:2000])

            # Feed observations back as a system-like message
            obs_text = "\n\n".join(observations)
            messages.append(HumanMessage(content=f"[Tool Results]\n\n{obs_text}"))

        # Max iterations reached
        yield ("token", "\n\n⚠️ Reached maximum iterations. Partial results above.", None)
        yield ("done", None, None)


def create_agent() -> CodeQAAgent:
    return CodeQAAgent()
