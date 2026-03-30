import logging
import chainlit as cl

from agent import create_agent

logger = logging.getLogger(__name__)

agent = create_agent()


@cl.on_chat_start
async def on_start():
    cl.user_session.set("thread_id", cl.context.session.id)
    await cl.Message(
        content="👋 我已准备好分析代码库，请问你想了解什么？"
    ).send()


@cl.on_message
async def on_message(message: cl.Message):
    thread_id = cl.user_session.get("thread_id")

    msg = cl.Message(content="")

    async for event_type, name, data in agent.astream_response(
        message.content, thread_id
    ):
        if event_type == "tool_start":
            step = cl.Step(name=name, type="tool")
            step.input = data or ""
            await step.send()
            cl.user_session.set(f"step_{name}", step)

        elif event_type == "tool_end":
            step = cl.user_session.get(f"step_{name}")
            if step:
                step.output = data or ""
                await step.update()

        elif event_type == "token":
            await msg.stream_token(name)

        elif event_type == "done":
            break

    await msg.send()
