"""Create database tables for Chainlit data persistence (PostgreSQL via SQLAlchemy)."""

import time
import sys

from sqlalchemy import create_engine, text

from config import settings

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    "id" TEXT PRIMARY KEY,
    "identifier" TEXT NOT NULL UNIQUE,
    "createdAt" TEXT,
    "metadata" TEXT DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS threads (
    "id" TEXT PRIMARY KEY,
    "createdAt" TEXT,
    "name" TEXT,
    "userId" TEXT,
    "userIdentifier" TEXT,
    "tags" TEXT,
    "metadata" TEXT DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS steps (
    "id" TEXT PRIMARY KEY,
    "name" TEXT,
    "type" TEXT,
    "threadId" TEXT REFERENCES threads("id") ON DELETE CASCADE,
    "parentId" TEXT,
    "streaming" BOOLEAN DEFAULT FALSE,
    "waitForAnswer" BOOLEAN DEFAULT FALSE,
    "isError" BOOLEAN DEFAULT FALSE,
    "input" TEXT,
    "output" TEXT,
    "createdAt" TEXT,
    "start" TEXT,
    "end" TEXT,
    "showInput" TEXT,
    "language" TEXT,
    "tags" TEXT,
    "metadata" TEXT DEFAULT '{}',
    "generation" TEXT,
    "defaultOpen" BOOLEAN DEFAULT FALSE,
    "autoCollapse" BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS elements (
    "id" TEXT PRIMARY KEY,
    "threadId" TEXT REFERENCES threads("id") ON DELETE CASCADE,
    "type" TEXT,
    "url" TEXT,
    "chainlitKey" TEXT,
    "name" TEXT,
    "display" TEXT,
    "objectKey" TEXT,
    "size" TEXT,
    "page" INTEGER,
    "language" TEXT,
    "forId" TEXT,
    "mime" TEXT,
    "props" TEXT DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS feedbacks (
    "id" TEXT PRIMARY KEY,
    "forId" TEXT NOT NULL,
    "threadId" TEXT REFERENCES threads("id") ON DELETE CASCADE,
    "value" INTEGER NOT NULL,
    "comment" TEXT,
    "strategy" TEXT DEFAULT 'binary'
);
"""

INDEXES = [
    'CREATE INDEX IF NOT EXISTS idx_threads_user ON threads ("userId")',
    'CREATE INDEX IF NOT EXISTS idx_steps_thread ON steps ("threadId")',
    'CREATE INDEX IF NOT EXISTS idx_elements_thread ON elements ("threadId")',
    'CREATE INDEX IF NOT EXISTS idx_feedbacks_thread ON feedbacks ("threadId")',
]


def init_db(database_url: str | None = None, retries: int = 10, delay: float = 3.0):
    url = database_url or settings.database_sync_url
    for attempt in range(1, retries + 1):
        try:
            engine = create_engine(url)
            with engine.connect() as conn:
                conn.execute(text(SCHEMA))
                for idx_sql in INDEXES:
                    conn.execute(text(idx_sql))
                conn.commit()
            engine.dispose()
            print(f"✅ Database initialized: {url.partition('@')[2] if '@' in url else url}")
            return
        except Exception as e:
            print(f"⚠️  Database init attempt {attempt}/{retries} failed: {e}")
            if attempt < retries:
                time.sleep(delay)
            else:
                raise


if __name__ == "__main__":
    url = sys.argv[1] if len(sys.argv) > 1 else None
    init_db(url)
