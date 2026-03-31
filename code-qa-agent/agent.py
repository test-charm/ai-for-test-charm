import json
import logging
import uuid
from typing import Any, Callable, Awaitable

from langchain_core.messages import SystemMessage, HumanMessage, AIMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langchain_anthropic import ChatAnthropic

from config import settings
from tools import list_directory, find_files, grep_code, read_file, get_symbols, get_repo_map

logger = logging.getLogger(__name__)

TOOLS = [list_directory, find_files, grep_code, read_file, get_symbols, get_repo_map]
TOOLS_MAP: dict[str, Any] = {t.name: t for t in TOOLS}

SYSTEM_PROMPT = """\
You are a code analyst assistant. You explore codebases to answer questions.
You MUST use the provided tools to explore the codebase before answering.
NEVER answer from memory alone — always verify by reading actual code.
Respond in the same language the user uses.
Be concise — give direct answers with code references (file paths and line numbers).
"""

ProgressCallback = Callable[[int, int, str | None], Awaitable[None]]

MAX_ITERATIONS = 100


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
    """ReAct agent using native OpenAI tool calling."""

    def __init__(self):
        self.llm = _create_llm()
        self.llm_with_tools = self.llm.bind_tools(TOOLS)
        self.conversations: dict[str, list] = {}

    def _get_messages(self, thread_id: str) -> list:
        if thread_id not in self.conversations:
            self.conversations[thread_id] = [
                SystemMessage(content=SYSTEM_PROMPT),
            ]
        return self.conversations[thread_id]

    async def astream_response(self, user_input: str, thread_id: str, progress_callback: ProgressCallback | None = None):
        """Run the ReAct loop, yielding events for the UI layer.

        Event types:
            ("tool_start", tool_name, tool_args_str)
            ("tool_end", tool_name, result_str)
            ("token", token_str)
            ("done", None, None)
        """
        messages = self._get_messages(thread_id)

        if len(messages) == 1:  # only system prompt — first question
            tree_result = _execute_tool("list_directory", {"path": ".", "max_depth": 2})
            messages.append(HumanMessage(content=f"Here is the project structure:\n{tree_result}\n\nNow answer my question: {user_input}"))
        else:
            messages.append(HumanMessage(content=user_input))

        for iteration in range(MAX_ITERATIONS):
            logger.info(f"Agent iteration {iteration + 1}/{MAX_ITERATIONS}")
            if progress_callback:
                await progress_callback(iteration + 1, MAX_ITERATIONS, None)

            response = await self.llm_with_tools.ainvoke(messages)
            logger.info(f"LLM response ({len(response.content)} chars content, {len(response.tool_calls)} tool calls)")
            messages.append(response)

            if not response.tool_calls:
                for token in response.content:
                    yield ("token", token, None)
                yield ("done", None, None)
                return

            for tc in response.tool_calls:
                tool_name = tc["name"]
                tool_args = tc["args"]
                tool_call_id = tc["id"]

                yield ("tool_start", tool_name, json.dumps(tool_args, ensure_ascii=False)[:500])

                if progress_callback:
                    await progress_callback(iteration + 1, MAX_ITERATIONS, tool_name)

                result = _execute_tool(tool_name, tool_args)
                truncated = result[:4000] if len(result) > 4000 else result

                messages.append(ToolMessage(content=truncated, tool_call_id=tool_call_id))
                yield ("tool_end", tool_name, truncated[:2000])

        yield ("token", "\n\n⚠️ Reached maximum iterations. Partial results above.", None)
        yield ("done", None, None)

    async def ask(self, question: str, thread_id: str | None = None, progress_callback: ProgressCallback | None = None) -> str:
        """Run the full ReAct loop and return the final answer (non-streaming)."""
        if thread_id is None:
            thread_id = str(uuid.uuid4())
        answer = ""
        async for event_type, token, _data in self.astream_response(question, thread_id, progress_callback):
            if event_type == "token":
                answer += token
        return answer


def create_agent() -> CodeQAAgent:
    return CodeQAAgent()
