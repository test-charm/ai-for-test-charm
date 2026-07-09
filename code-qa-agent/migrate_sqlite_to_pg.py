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


def _resolve_collisions(
    conn, src: sqlite3.Connection, table: str, unique_col: str
) -> dict[str, str]:
    """Build old_id → new_id mapping for rows that conflict on `unique_col`.
    
    When the app already ran before migration, PG may have users/threads with
    the same identifier/name but different UUIDs.  We must remap foreign keys.
    """
    mapping: dict[str, str] = {}
    try:
        old_rows = src.execute(f'SELECT id, "{unique_col}" FROM "{table}"').fetchall()
    except sqlite3.OperationalError:
        return mapping

    for old in old_rows:
        unique_val = old[unique_col]
        if unique_val is None:
            continue
        existing = conn.execute(
            text(f'SELECT id FROM "{table}" WHERE "{unique_col}" = :val'),
            {"val": unique_val},
        ).fetchone()
        if existing and existing[0] != old["id"]:
            mapping[old["id"]] = existing[0]
            print(f"⚠️  {table} collision: {old['id']} → {existing[0]} ({unique_col}={unique_val})")

    return mapping


def _remap_fk(
    conn, table: str, fk_col: str, mapping: dict[str, str]
) -> int:
    """Update foreign key references from old IDs to new IDs."""
    updated = 0
    for old_id, new_id in mapping.items():
        result = conn.execute(
            text(f'UPDATE "{table}" SET "{fk_col}" = :new WHERE "{fk_col}" = :old'),
            {"new": new_id, "old": old_id},
        )
        updated += result.rowcount
    return updated


def migrate(sqlite_path: str, pg_url: str):
    sqlite_path = Path(sqlite_path)
    if not sqlite_path.exists():
        print(f"❌ SQLite database not found: {sqlite_path}")
        sys.exit(1)

    src = sqlite3.connect(str(sqlite_path))
    src.row_factory = sqlite3.Row

    engine = create_engine(pg_url)

    with engine.begin() as conn:
        # ── users ──────────────────────────────────────────
        user_map = _resolve_collisions(conn, src, "users", "identifier")

        try:
            rows = src.execute("SELECT * FROM users").fetchall()
        except sqlite3.OperationalError as e:
            print(f"⚠️  Skipping users: {e}")
            rows = []

        columns = [desc[0] for desc in src.execute("SELECT * FROM users LIMIT 0").description]
        col_names = ", ".join(f'"{c}"' for c in columns)
        ph = ", ".join(f":{c}" for c in columns)
        sql = f'INSERT INTO users ({col_names}) VALUES ({ph}) ON CONFLICT DO NOTHING'
        for row in rows:
            conn.execute(text(sql), {c: row[c] for c in columns})
        print(f"✅ users: {len(rows)} rows migrated ({len(user_map)} collisions resolved)")

        # ── threads ────────────────────────────────────────
        thread_map = _resolve_collisions(conn, src, "threads", "name")

        try:
            rows = src.execute("SELECT * FROM threads").fetchall()
        except sqlite3.OperationalError as e:
            print(f"⚠️  Skipping threads: {e}")
            rows = []

        columns = [desc[0] for desc in src.execute("SELECT * FROM threads LIMIT 0").description]
        col_names = ", ".join(f'"{c}"' for c in columns)
        ph = ", ".join(f":{c}" for c in columns)
        sql = f'INSERT INTO threads ({col_names}) VALUES ({ph}) ON CONFLICT DO NOTHING'
        for row in rows:
            values = {c: row[c] for c in columns}
            if values.get("userId") in user_map:
                values["userId"] = user_map[values["userId"]]
            conn.execute(text(sql), values)
        print(f"✅ threads: {len(rows)} rows migrated ({len(thread_map)} collisions resolved)")

        # Remap userId in threads that were already inserted with old user IDs
        remapped = _remap_fk(conn, "threads", "userId", user_map)
        if remapped:
            print(f"   ↳ remapped {remapped} threads.userId references")

        # ── steps ──────────────────────────────────────────
        try:
            rows = src.execute("SELECT * FROM steps").fetchall()
        except sqlite3.OperationalError as e:
            print(f"⚠️  Skipping steps: {e}")
            rows = []

        columns = [desc[0] for desc in src.execute("SELECT * FROM steps LIMIT 0").description]
        col_names = ", ".join(f'"{c}"' for c in columns)
        ph = ", ".join(f":{c}" for c in columns)
        sql = f'INSERT INTO steps ({col_names}) VALUES ({ph}) ON CONFLICT DO NOTHING'
        all_maps = {**user_map, **thread_map}
        for row in rows:
            values = _cast_bools("steps", {c: row[c] for c in columns})
            for fk in ("threadId", "parentId"):
                if values.get(fk) in all_maps:
                    values[fk] = all_maps[values[fk]]
            conn.execute(text(sql), values)
        print(f"✅ steps: {len(rows)} rows migrated")

        remapped = _remap_fk(conn, "steps", "threadId", all_maps)
        if remapped:
            print(f"   ↳ remapped {remapped} steps.threadId/parentId references")

        # ── elements ───────────────────────────────────────
        for table in ("elements", "feedbacks"):
            try:
                rows = src.execute(f"SELECT * FROM {table}").fetchall()
            except sqlite3.OperationalError as e:
                print(f"⚠️  Skipping {table}: {e}")
                continue

            if not rows:
                print(f"⏭️  {table}: 0 rows, skipping")
                continue

            columns = [desc[0] for desc in src.execute(f"SELECT * FROM {table} LIMIT 0").description]
            col_names = ", ".join(f'"{c}"' for c in columns)
            ph = ", ".join(f":{c}" for c in columns)
            sql = f'INSERT INTO "{table}" ({col_names}) VALUES ({ph}) ON CONFLICT DO NOTHING'
            for row in rows:
                values = {c: row[c] for c in columns}
                if values.get("threadId") in all_maps:
                    values["threadId"] = all_maps[values["threadId"]]
                if values.get("forId") in all_maps:
                    values["forId"] = all_maps[values["forId"]]
                conn.execute(text(sql), values)
            print(f"✅ {table}: {len(rows)} rows migrated")

            remapped = _remap_fk(conn, table, "threadId", all_maps)
            if remapped:
                print(f"   ↳ remapped {remapped} {table}.threadId references")

    src.close()
    engine.dispose()
    print("✅ Migration complete")


if __name__ == "__main__":
    sqlite_path = sys.argv[1] if len(sys.argv) > 1 else "./data/chat_history.db"
    migrate(sqlite_path, settings.database_sync_url)
