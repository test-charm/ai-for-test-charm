import unittest

from langchain_core.messages import AIMessage, HumanMessage

from agent import (
    CodeQAAgent,
    _looks_like_incomplete_response,
    _required_tool_choice,
    _response_text,
)


class FakeBoundLLM:
    def __init__(self, responses):
        self._responses = list(responses)
        self.calls = []

    async def ainvoke(self, messages):
        self.calls.append(list(messages))
        if not self._responses:
            raise AssertionError("No fake responses left")
        return self._responses.pop(0)


class FakeLLM:
    def __init__(self, auto_responses=None, required_responses=None):
        self.bind_calls = []
        self.auto_bound = FakeBoundLLM(auto_responses or [])
        self.required_bound = FakeBoundLLM(required_responses or [])

    def bind_tools(self, tools, *, tool_choice=None, **kwargs):
        self.bind_calls.append(tool_choice)
        if tool_choice in ("required", "any"):
            return self.required_bound
        return self.auto_bound


class CodeQAAgentTest(unittest.IsolatedAsyncioTestCase):
    async def test_first_round_uses_required_tool_choice(self):
        llm = FakeLLM(
            auto_responses=[AIMessage(content="Verified answer", tool_calls=[])],
            required_responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "list_directory",
                            "args": {"path": "."},
                            "id": "call-1",
                            "type": "tool_call",
                        }
                    ],
                )
            ],
        )

        agent = CodeQAAgent(llm=llm, provider="openai")

        answer = await agent.ask("How is the repo organized?", thread_id="thread-1")

        self.assertEqual(answer, "Verified answer")
        self.assertEqual(llm.required_bound.calls.__len__(), 1)
        self.assertEqual(llm.auto_bound.calls.__len__(), 1)
        self.assertIn("required", llm.bind_calls)

    async def test_retries_if_model_answers_before_using_any_tool(self):
        llm = FakeLLM(
            auto_responses=[AIMessage(content="Verified answer", tool_calls=[])],
            required_responses=[
                AIMessage(content="Let me explore the codebase first.", tool_calls=[]),
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "list_directory",
                            "args": {"path": "."},
                            "id": "call-2",
                            "type": "tool_call",
                        }
                    ],
                ),
            ],
        )

        agent = CodeQAAgent(llm=llm, provider="openai")

        answer = await agent.ask("Where is the DAL logic?", thread_id="thread-2")

        self.assertEqual(answer, "Verified answer")
        self.assertEqual(llm.required_bound.calls.__len__(), 2)
        self.assertEqual(llm.auto_bound.calls.__len__(), 1)
        self.assertIsInstance(llm.required_bound.calls[1][-1], HumanMessage)
        self.assertIn("must call at least one tool", llm.required_bound.calls[1][-1].content.lower())

    async def test_retries_if_post_tool_response_is_only_planning_text(self):
        llm = FakeLLM(
            auto_responses=[
                AIMessage(
                    content="Now I have a thorough understanding of DAL syntax. Let me also look at chained method calls:",
                    tool_calls=[],
                ),
                AIMessage(content="Verified answer with file references", tool_calls=[]),
            ],
            required_responses=[
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "list_directory",
                            "args": {"path": "."},
                            "id": "call-3",
                            "type": "tool_call",
                        }
                    ],
                )
            ],
        )

        agent = CodeQAAgent(llm=llm, provider="openai")

        answer = await agent.ask("Explain DAL syntax", thread_id="thread-3")

        self.assertEqual(answer, "Verified answer with file references")
        self.assertEqual(llm.required_bound.calls.__len__(), 1)
        self.assertEqual(llm.auto_bound.calls.__len__(), 2)
        self.assertIsInstance(llm.auto_bound.calls[1][-1], HumanMessage)
        self.assertIn("do not describe your next step", llm.auto_bound.calls[1][-1].content.lower())


class AgentHelpersTest(unittest.TestCase):
    def test_required_tool_choice_depends_on_provider(self):
        self.assertEqual(_required_tool_choice("openai"), "required")
        self.assertEqual(_required_tool_choice("anthropic"), "any")

    def test_response_text_handles_string_and_blocks(self):
        self.assertEqual(_response_text("plain text"), "plain text")
        self.assertEqual(
            _response_text(
                [
                    {"type": "text", "text": "hello"},
                    {"type": "tool_use", "name": "list_directory"},
                    {"type": "text", "text": " world"},
                ]
            ),
            "hello world",
        )

    def test_incomplete_response_detection(self):
        self.assertTrue(
            _looks_like_incomplete_response(
                "Now I have a thorough understanding of DAL syntax. Let me also look at chained method calls:"
            )
        )
        self.assertFalse(
            _looks_like_incomplete_response(
                "DAL expressions are evaluated in Foo.java:12 and Bar.java:28."
            )
        )
