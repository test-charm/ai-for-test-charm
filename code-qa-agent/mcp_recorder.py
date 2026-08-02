"""Persist MCP request/response records to PostgreSQL via SQLAlchemy ORM."""

import logging
import uuid
from datetime import datetime, timezone

from sqlalchemy import Column, Text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from config import settings

logger = logging.getLogger(__name__)

MCP_REQUESTS_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS mcp_requests (
    "id" TEXT PRIMARY KEY,
    "question" TEXT NOT NULL,
    "answer" TEXT NOT NULL,
    "provider" TEXT,
    "model" TEXT,
    "created_at" TEXT
);
"""


class Base(DeclarativeBase):
    pass


class McpRequest(Base):
    __tablename__ = "mcp_requests"

    id = Column(Text, primary_key=True)
    question = Column(Text, nullable=False)
    answer = Column(Text, nullable=False)
    provider = Column(Text)
    model = Column(Text)
    created_at = Column(Text)


_engine = None
_sessionmaker = None


def _get_sessionmaker() -> sessionmaker:
    global _engine, _sessionmaker
    if _engine is None:
        _engine = create_async_engine(settings.database_url, echo=False)
    if _sessionmaker is None:
        _sessionmaker = sessionmaker(_engine, class_=AsyncSession, expire_on_commit=False)
    return _sessionmaker


async def save_mcp_request(
    question: str,
    answer: str,
    provider: str = "",
    model: str = "",
) -> None:
    """Save an MCP question/answer pair to the database."""
    try:
        sm = _get_sessionmaker()
        async with sm() as session:
            session.add(McpRequest(
                id=str(uuid.uuid4()),
                question=question,
                answer=answer,
                provider=provider,
                model=model,
                created_at=datetime.now(timezone.utc).isoformat(),
            ))
            await session.commit()
    except Exception:
        logger.exception("Failed to save MCP request record")
