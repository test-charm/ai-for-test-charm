# --- coverage bootstrap (driven by COVERAGE_DATA_FILE env var) ---
import os as _os
_coverage_data_file = _os.environ.get("COVERAGE_DATA_FILE", "")
if _coverage_data_file:
    import atexit as _atexit, signal as _signal, sys as _sys
    import coverage as _coverage
    _rcfile = _os.environ.get("COVERAGE_RCFILE", ".coveragerc")
    _cov = _coverage.Coverage(data_file=_coverage_data_file, config_file=_rcfile, branch=True)
    _cov.start()
    # Touch a marker file to verify coverage bootstrap ran
    _marker_dir = _os.path.dirname(_coverage_data_file)
    _os.makedirs(_marker_dir, exist_ok=True)
    with open(_os.path.join(_marker_dir, "bootstrap-ran.txt"), "w") as _f:
        _f.write(f"coverage bootstrap ran at pid={_os.getpid()}\n")
    def _save_coverage():
        _cov.stop()
        _cov.save()
    _atexit.register(_save_coverage)
    for _sig in (_signal.SIGTERM, _signal.SIGINT):
        _signal.signal(_sig, lambda signum, frame, s=_sig: (
            _save_coverage(), _signal.signal(s, _signal.SIG_DFL), _os.kill(_os.getpid(), s)
        ))
# --- end coverage bootstrap ---

import logging
import time
import chainlit as cl
from chainlit.data.sql_alchemy import SQLAlchemyDataLayer

from agent import create_agent
from config import settings

logger = logging.getLogger(__name__)

agent = create_agent()


def _preview_text(text: str, limit: int = 200) -> str:
    compact = " ".join(text.split())
    if len(compact) <= limit:
        return compact
    return compact[:limit] + "..."


@cl.data_layer
def get_data_layer():
    return SQLAlchemyDataLayer(conninfo=settings.database_url)


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
    tool_calls = 0

    logger.info(
        "Chat request thread=%s chars=%d preview=%r",
        thread_id,
        len(message.content),
        _preview_text(message.content),
    )

    msg = cl.Message(content="")

    async for event_type, name, data in agent.astream_response(
        message.content, thread_id
    ):
        if event_type == "token":
            await msg.stream_token(name)
        elif event_type == "tool_start":
            tool_calls += 1
        elif event_type == "done":
            break

    elapsed = time.monotonic() - start
    minutes, seconds = divmod(int(elapsed), 60)
    if minutes > 0:
        time_str = f"{minutes}分{seconds}秒"
    else:
        time_str = f"{seconds}秒"
    await msg.stream_token(f"\n\n---\n⏱️ 耗时 {time_str}")

    logger.info(
        "Chat response thread=%s elapsed=%.2fs tool_calls=%d",
        thread_id,
        elapsed,
        tool_calls,
    )
    await msg.send()

    # Save coverage data after each response (for e2e test coverage collection)
    if _coverage_data_file:
        try:
            _save_coverage()
        except Exception:
            pass
