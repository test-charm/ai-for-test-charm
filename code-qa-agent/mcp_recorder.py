"""Persist MCP request/response records to PostgreSQL."""

import asyncio
import logging
import uuid
from datetime import datetime, timezone

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


async def save_mcp_request(
    question: str,
    answer: str,
    provider: str = "",
    model: str = "",
) -> None:
    """Save an MCP question/answer pair to the database."""
    try:
        conn = await asyncio.wait_for(
            _get_connection(),
            timeout=5.0,
        )
        try:
            await conn.execute(
                "INSERT INTO mcp_requests (\"id\", \"question\", \"answer\", \"provider\", \"model\", \"created_at\") "
                "VALUES ($1, $2, $3, $4, $5, $6)",
                str(uuid.uuid4()),
                question,
                answer,
                provider,
                model,
                datetime.now(timezone.utc).isoformat(),
            )
        finally:
            await conn.close()
    except Exception:
        logger.exception("Failed to save MCP request record")


async def _get_connection():
    """Create a new asyncpg connection."""
    import asyncpg
    return await asyncpg.connect(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
