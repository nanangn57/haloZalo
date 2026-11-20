#!/usr/bin/env python3
"""Agent: friends — request, accept, list both sides."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import USER_A, USER_B, Report, login, req  # noqa: E402


def run() -> Report:
    report = Report("friends")
    print(f"\n=== Agent {report.agent} ===")

    tok_a, user_a = login(USER_A)
    tok_b, user_b = login(USER_B)
    uid_a, uid_b = user_a["userId"], user_b["userId"]

    status, body = req("POST", "/friends/requests", tok_a, {"userId": uid_b})
    report.ok("send friend request (201 or already 409)", status in (201, 409), str(status))

    if status == 201:
        status, body = req("POST", f"/friends/requests/{uid_a}/accept", tok_b)
        report.ok("accept friend request", status == 200, str(body))
    else:
        report.ok("accept skipped (already friends/request)", True)

    status, body = req("GET", "/friends", tok_a)
    ids = {f["userId"] for f in body.get("friends", [])}
    report.ok("A lists B", uid_b in ids, str(ids))

    status, body = req("GET", "/friends", tok_b)
    ids = {f["userId"] for f in body.get("friends", [])}
    report.ok("B lists A", uid_a in ids, str(ids))

    status, body = req("POST", "/friends/requests", tok_a, {"userId": uid_a})
    report.ok("cannot friend yourself", status in (400, 409), str(status))

    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
