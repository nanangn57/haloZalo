#!/usr/bin/env python3
"""Agent: auth + profile — login, 401, PATCH /me, lookup displayName, find by email."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import USER_A, USER_B, Report, login, login_email, req  # noqa: E402


def run() -> Report:
    report = Report("auth_profile")
    print(f"\n=== Agent {report.agent} ===")

    status, body = req("GET", "/me")
    report.ok("no token → 401 /me", status == 401, str(body.get("error", body)))

    status, body = req("POST", "/auth/login", body={"login": login_email(USER_A), "password": "wrong-password", "deviceId": "e2e-badpass-0001"})
    report.ok("bad password → 401", status == 401)

    tok_a, user_a = login(USER_A)
    tok_b, user_b = login(USER_B)
    report.ok("login A", bool(tok_a and user_a.get("userId")), user_a.get("email") or user_a.get("username", ""))
    report.ok("login B", bool(tok_b and user_b.get("userId")), user_b.get("email") or user_b.get("username", ""))

    status, me = req("GET", "/me", tok_a)
    report.ok("GET /me with token", status == 200 and me.get("userId") == user_a["userId"])

    status, patched = req(
        "PATCH",
        "/me",
        tok_a,
        {
            "displayName": "Ngoc Anh Agent",
            "avatar": "https://example.com/agent.png",
            "bio": "haloZalo e2e bio",
        },
    )
    report.ok(
        "PATCH /me displayName+avatar+bio",
        status == 200
        and patched.get("displayName") == "Ngoc Anh Agent"
        and patched.get("bio") == "haloZalo e2e bio",
        patched.get("displayName", ""),
    )

    status, lookup = req("GET", f"/users/lookup?ids={user_a['userId']}", tok_b)
    users = {u["userId"]: u for u in lookup.get("users", [])}
    report.ok(
        "lookup shows new displayName",
        users.get(user_a["userId"], {}).get("displayName") == "Ngoc Anh Agent",
        str(users.get(user_a["userId"])),
    )

    # features.md: tìm bạn qua email (login identity cũng là email)
    query = user_b.get("email") or USER_B
    status, found = req("GET", f"/users?q={query}", tok_a)
    report.ok(
        "find user by email",
        status == 200
        and (
            found.get("email") == query
            or found.get("userId") == user_b["userId"]
            or found.get("username") == user_b.get("username")
        ),
        str(found),
    )

    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
