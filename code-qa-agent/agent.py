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


def _required_tool_choice(provider: str) -> str:
    return "any" if provider == "anthropic" else "required"


def _preview_text(text: str, limit: int = 200) -> str:
    compact = " ".join(text.split())
    if len(compact) <= limit:
        return compact
    return compact[:limit] + "..."


def _response_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if content is None:
        return ""
    if isinstance(content, list):
        chunks: list[str] = []
        for block in content:
            if isinstance(block, str):
                chunks.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                text = block.get("text")
                if isinstance(text, str):
                    chunks.append(text)
            else:
                text = getattr(block, "text", None)
                if isinstance(text, str):
                    chunks.append(text)
        return "".join(chunks)
    return str(content)


def _looks_like_incomplete_response(text: str) -> bool:
    normalized = _preview_text(text, limit=1000).lower()
    if not normalized:
        return False

    planning_markers = (
        "let me also look",
        "let me look",
        "let me inspect",
        "let me explore",
        "let me check",
        "let me search",
        "i'll look",
        "i will look",
        "i should look",
        "next, i'll",
        "next, i will",
    )
    if any(marker in normalized for marker in planning_markers):
        return True

    return normalized.endswith(":") and any(
        marker in normalized for marker in ("let me", "i'll", "i will", "next,")
    )


def _response_stop_reason(response: AIMessage) -> str:
    response_metadata = getattr(response, "response_metadata", None) or {}
    additional_kwargs = getattr(response, "additional_kwargs", None) or {}
    for container in (response_metadata, additional_kwargs):
        for key in ("finish_reason", "stop_reason"):
            value = container.get(key)
            if value:
                return str(value)
    return "unknown"


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

    def __init__(self, llm: Any | None = None, provider: str | None = None):
        self.provider = provider or settings.llm_provider
        self.llm = llm or _create_llm()
        self.model = getattr(self.llm, "model_name", None) or getattr(self.llm, "model", None) or settings.llm_model
        self.llm_with_tools = self.llm.bind_tools(TOOLS)
        self.llm_with_required_tool = self.llm.bind_tools(
            TOOLS,
            tool_choice=_required_tool_choice(self.provider),
        )
        self.conversations: dict[str, list] = {}

    def _get_messages(self, thread_id: str) -> list:
        if thread_id not in self.conversations:
            self.conversations[thread_id] = [
                SystemMessage(content=SYSTEM_PROMPT),
            ]
        return self.conversations[thread_id]

    @staticmethod
    def _has_tool_results(messages: list) -> bool:
        return any(isinstance(message, ToolMessage) for message in messages)

    async def astream_response(self, user_input: str, thread_id: str, progress_callback: ProgressCallback | None = None):
        """Run the ReAct loop, yielding events for the UI layer.

        Event types:
            ("tool_start", tool_name, tool_args_str)
            ("tool_end", tool_name, result_str)
            ("token", token_str)
            ("done", None, None)
        """
        messages = self._get_messages(thread_id)
        logger.info(
            "Agent request thread=%s provider=%s model=%s question=%r",
            thread_id,
            self.provider,
            self.model,
            _preview_text(user_input),
        )

        if len(messages) == 1:  # only system prompt — first question
            tree_result = _execute_tool("list_directory", {"path": ".", "max_depth": 2})
            logger.info(
                "Seeded project tree thread=%s chars=%d",
                thread_id,
                len(tree_result),
            )
            messages.append(HumanMessage(content=f"Here is the project structure:\n{tree_result}\n\nNow answer my question: {user_input}"))
        else:
            messages.append(HumanMessage(content=user_input))

        for iteration in range(MAX_ITERATIONS):
            has_tool_results = self._has_tool_results(messages)
            phase = "auto" if has_tool_results else _required_tool_choice(self.provider)
            logger.info(
                "Agent iteration %d/%d thread=%s mode=%s messages=%d has_tool_results=%s",
                iteration + 1,
                MAX_ITERATIONS,
                thread_id,
                phase,
                len(messages),
                has_tool_results,
            )
            if progress_callback:
                await progress_callback(iteration + 1, MAX_ITERATIONS, None)

            llm = self.llm_with_tools if has_tool_results else self.llm_with_required_tool
            response = await llm.ainvoke(messages)
            response_text = _response_text(response.content)
            tool_calls = response.tool_calls or []
            logger.info(
                "LLM response thread=%s chars=%d tool_calls=%d stop_reason=%s preview=%r",
                thread_id,
                len(response_text),
                len(tool_calls),
                _response_stop_reason(response),
                _preview_text(response_text),
            )
            for idx, tool_call in enumerate(tool_calls, start=1):
                logger.info(
                    "Tool requested thread=%s %d/%d name=%s args=%s",
                    thread_id,
                    idx,
                    len(tool_calls),
                    tool_call["name"],
                    _preview_text(json.dumps(tool_call["args"], ensure_ascii=False)),
                )
            messages.append(response)

            if not tool_calls:
                if not has_tool_results:
                    logger.warning(
                        "Model answered without using any tools first; retrying thread=%s preview=%r",
                        thread_id,
                        _preview_text(response_text),
                    )
                    messages.append(
                        HumanMessage(
                            content=(
                                "You have not used any tools yet. "
                                "You must call at least one tool before answering. "
                                "Choose the most relevant tool and call it now."
                            )
                        )
                    )
                    continue

                if _looks_like_incomplete_response(response_text):
                    logger.warning(
                        "Model returned planning text instead of a final answer; retrying thread=%s preview=%r",
                        thread_id,
                        _preview_text(response_text),
                    )
                    messages.append(
                        HumanMessage(
                            content=(
                                "Do not describe your next step. "
                                "You already have enough information from the tool results. "
                                "Give the final answer now with file paths and line numbers."
                            )
                        )
                    )
                    continue

                logger.info(
                    "Returning final answer thread=%s chars=%d preview=%r",
                    thread_id,
                    len(response_text),
                    _preview_text(response_text),
                )
                for token in response_text:
                    yield ("token", token, None)
                yield ("done", None, None)
                return

            for tc in tool_calls:
                tool_name = tc["name"]
                tool_args = tc["args"]
                tool_call_id = tc.get("id") or f"{tool_name}-{iteration}"
                tool_args_preview = _preview_text(json.dumps(tool_args, ensure_ascii=False))

                yield ("tool_start", tool_name, json.dumps(tool_args, ensure_ascii=False)[:500])

                if progress_callback:
                    await progress_callback(iteration + 1, MAX_ITERATIONS, tool_name)

                logger.info(
                    "Executing tool thread=%s name=%s args=%s",
                    thread_id,
                    tool_name,
                    tool_args_preview,
                )
                result = _execute_tool(tool_name, tool_args)
                truncated = result[:4000] if len(result) > 4000 else result
                logger.info(
                    "Tool result thread=%s name=%s chars=%d preview=%r",
                    thread_id,
                    tool_name,
                    len(result),
                    _preview_text(truncated),
                )

                messages.append(ToolMessage(content=truncated, tool_call_id=tool_call_id))
                yield ("tool_end", tool_name, truncated[:2000])

        logger.warning("Agent reached maximum iterations thread=%s", thread_id)
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
