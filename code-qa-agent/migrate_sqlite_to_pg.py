"""Migrate data from SQLite chat_history.db to PostgreSQL.

Usage:
    python migrate_sqlite_to_pg.py [sqlite_db_path]

Environment variables (CQA_DB_* prefix) control the PostgreSQL target.
"""

import sqlite3
import sys
from pathlib import Path

from sqlalchemy import create_engine, text

from config import settings

TABLES = ["users", "threads", "steps", "elements", "feedbacks"]

# Tables with foreign keys — insert order matters
INSERT_ORDER = ["users", "threads", "steps", "elements", "feedbacks"]

# Boolean columns per table — SQLite stores 0/1, PostgreSQL needs True/False
BOOLEAN_COLUMNS = {
    "steps": {"streaming", "waitForAnswer", "isError", "defaultOpen", "autoCollapse"},
}


def _cast_bools(table: str, values: dict) -> dict:
    bool_cols = BOOLEAN_COLUMNS.get(table, set())
    for col in bool_cols:
        if col in values and values[col] is not None:
            values[col] = bool(values[col])
    return values


def migrate(sqlite_path: str, pg_url: str):
    sqlite_path = Path(sqlite_path)
    if not sqlite_path.exists():
        print(f"❌ SQLite database not found: {sqlite_path}")
        sys.exit(1)

    src = sqlite3.connect(str(sqlite_path))
    src.row_factory = sqlite3.Row

    engine = create_engine(pg_url)

    with engine.begin() as conn:
        for table in INSERT_ORDER:
            try:
                rows = src.execute(f"SELECT * FROM {table}").fetchall()
            except sqlite3.OperationalError as e:
                print(f"⚠️  Skipping {table}: {e}")
                continue

            if not rows:
                print(f"⏭️  {table}: 0 rows, skipping")
                continue

            columns = [desc[0] for desc in src.execute(f"SELECT * FROM {table} LIMIT 0").description]
            placeholders = ", ".join(f":{col}" for col in columns)
            col_names = ", ".join(f'"{col}"' for col in columns)
            insert_sql = f'INSERT INTO "{table}" ({col_names}) VALUES ({placeholders}) ON CONFLICT DO NOTHING'

            count = 0
            for row in rows:
                values = _cast_bools(table, {col: row[col] for col in columns})
                conn.execute(text(insert_sql), values)
                count += 1

            print(f"✅ {table}: {count} rows migrated")

    src.close()
    engine.dispose()
    print("✅ Migration complete")


if __name__ == "__main__":
    sqlite_path = sys.argv[1] if len(sys.argv) > 1 else "./data/chat_history.db"
    migrate(sqlite_path, settings.database_sync_url)
