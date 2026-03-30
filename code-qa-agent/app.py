import logging
import chainlit as cl
from chainlit.data.sql_alchemy import SQLAlchemyDataLayer

from agent import create_agent

logger = logging.getLogger(__name__)

agent = create_agent()


@cl.data_layer
def get_data_layer():
    return SQLAlchemyDataLayer(conninfo="sqlite+aiosqlite:///./data/chat_history.db")


@cl.header_auth_callback
async def header_auth(headers: dict) -> cl.User | None:
    # Generate a stable user ID per browser via Chainlit's session cookie
    # Each distinct browser gets its own chat history
    import hashlib
    raw = headers.get("cookie", "") or headers.get("user-agent", "anonymous")
    uid = hashlib.sha256(raw.encode()).hexdigest()[:16]
    return cl.User(identifier=f"user-{uid}", metadata={"role": "user"})


@cl.on_chat_start
async def on_start():
    cl.user_session.set("thread_id", cl.context.session.id)
    await cl.Message(
        content="👋 我已准备好分析代码库，请问你想了解什么？"
    ).send()


@cl.on_chat_resume
async def on_chat_resume(thread):
    cl.user_session.set("thread_id", thread["id"])


@cl.on_message
async def on_message(message: cl.Message):
    thread_id = cl.user_session.get("thread_id")

    msg = cl.Message(content="")

    async for event_type, name, data in agent.astream_response(
        message.content, thread_id
    ):
        if event_type == "token":
            await msg.stream_token(name)
        elif event_type == "done":
            break

    await msg.send()
