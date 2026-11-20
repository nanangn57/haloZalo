#!/usr/bin/env python3
"""Agent: analytics dashboard + device push token registration."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import USER_A, USER_B, Report, login, new_id, req  # noqa: E402


def run() -> Report:
    report = Report("dashboard_devices")
    print(f"\n=== Agent {report.agent} ===")

    tok_a, _ = login(USER_A)
    tok_b, _ = login(USER_B)

    status, stats = req("GET", "/stats", tok_a)
    report.ok(
        "GET /stats",
        status == 200 and all(k in stats for k in ("logins", "groupsCreated", "messagesCreated", "mediaUploads")),
        str(stats),
    )

    status, dash = req("GET", "/stats/dashboard", tok_a)
    report.ok(
        "GET /stats/dashboard",
        status == 200 and "activeUsers" in dash and "messagesByDay" in dash,
        f"activeUsers={dash.get('activeUsers')}",
    )

    status, activity = req("GET", "/me/activity", tok_a)
    report.ok(
        "GET /me/activity",
        status == 200 and activity.get("logins", 0) >= 1,
        str(activity),
    )

    status, _ = req("GET", "/me/activity")
    report.ok("activity without token → 401", status == 401)

    token = f"demo-agent-{new_id()[:12]}"
    status, _ = req("POST", "/devices", tok_b, {"token": token, "platform": "android"})
    report.ok("register device token", status == 204)

    status, _ = req("DELETE", "/devices", tok_b, {"token": token})
    report.ok("unregister device token", status == 204)

    status, _ = req("POST", "/devices", tok_b, {"token": token, "platform": "ios"})
    report.ok("re-register device token", status == 204)

    status, _ = req("POST", "/devices", body={"token": token, "platform": "android"})
    report.ok("register without auth → 401", status == 401)

    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
