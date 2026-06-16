#!/usr/bin/env python3
"""Drop the legacy request_cache table from the local SQLite database."""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Drop the legacy request_cache table and vacuum the SQLite database."
    )
    parser.add_argument(
        "--db",
        default="data/media.db",
        help="SQLite database path. Defaults to data/media.db.",
    )
    return parser.parse_args()


def ensure_sqlite_database(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(f"database not found: {path}")
    if not path.is_file():
        raise ValueError(f"database path is not a file: {path}")

    header = path.read_bytes()[:16]
    if header != b"SQLite format 3\x00":
        raise ValueError(f"not a SQLite database: {path}")


def cleanup(path: Path) -> None:
    ensure_sqlite_database(path)
    with sqlite3.connect(path) as conn:
        before = conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='request_cache'"
        ).fetchone()
        conn.execute("DROP TABLE IF EXISTS request_cache")
        conn.commit()
        conn.execute("VACUUM")
        conn.commit()
    status = "dropped" if before else "already absent"
    print(f"request_cache table {status}: {path}")


def main() -> int:
    args = parse_args()
    db_path = Path(args.db).expanduser().resolve()
    try:
        cleanup(db_path)
        return 0
    except Exception as exc:
        print(f"cleanup failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
