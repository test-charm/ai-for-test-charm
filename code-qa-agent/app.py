import logging
import time
import chainlit as cl
from chainlit.data.sql_alchemy import SQLAlchemyDataLayer

from agent import create_agent
from config import settings

logger = logging.getLogger(__name__)

agent = create_agent()


@cl.data_layer
def get_data_layer():
    return SQLAlchemyDataLayer(conninfo="sqlite+aiosqlite:///./data/chat_history.db")


@cl.password_auth_callback
async def auth_callback(username: str, password: str) -> cl.User | None:
    if settings.auth_password and password != settings.auth_password:
        return None
    if not username.strip():
        return None
    return cl.User(identifier=username.strip(), metadata={"role": "user"})


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
    start = time.monotonic()

    msg = cl.Message(content="")

    async for event_type, name, data in agent.astream_response(
        message.content, thread_id
    ):
        if event_type == "token":
            await msg.stream_token(name)
        elif event_type == "done":
            break

    elapsed = time.monotonic() - start
    minutes, seconds = divmod(int(elapsed), 60)
    if minutes > 0:
        time_str = f"{minutes}分{seconds}秒"
    else:
        time_str = f"{seconds}秒"
    await msg.stream_token(f"\n\n---\n⏱️ 耗时 {time_str}")

    await msg.send()
