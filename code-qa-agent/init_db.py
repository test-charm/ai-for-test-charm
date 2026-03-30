"""Create SQLite tables for Chainlit data persistence."""

import sqlite3
import sys
from pathlib import Path

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
    "threadId" TEXT,
    "parentId" TEXT,
    "streaming" BOOLEAN DEFAULT 0,
    "waitForAnswer" BOOLEAN DEFAULT 0,
    "isError" BOOLEAN DEFAULT 0,
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
    "defaultOpen" BOOLEAN DEFAULT 0,
    "autoCollapse" BOOLEAN DEFAULT 0,
    FOREIGN KEY ("threadId") REFERENCES threads("id") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS elements (
    "id" TEXT PRIMARY KEY,
    "threadId" TEXT,
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
    "props" TEXT DEFAULT '{}',
    FOREIGN KEY ("threadId") REFERENCES threads("id") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS feedbacks (
    "id" TEXT PRIMARY KEY,
    "forId" TEXT NOT NULL,
    "threadId" TEXT,
    "value" INTEGER NOT NULL,
    "comment" TEXT,
    "strategy" TEXT DEFAULT 'binary',
    FOREIGN KEY ("threadId") REFERENCES threads("id") ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_threads_user ON threads("userId");
CREATE INDEX IF NOT EXISTS idx_steps_thread ON steps("threadId");
CREATE INDEX IF NOT EXISTS idx_elements_thread ON elements("threadId");
CREATE INDEX IF NOT EXISTS idx_feedbacks_thread ON feedbacks("threadId");
"""


def init_db(db_path: str = "./data/chat_history.db"):
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.executescript(SCHEMA)
    conn.close()
    print(f"✅ Database initialized: {db_path}")


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "./data/chat_history.db"
    init_db(path)
