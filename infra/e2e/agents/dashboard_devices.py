#!/usr/bin/env python3
"""Agent: analytics dashboard + device push token registration.

Non-admin users must get 403 on platform stats. Platform admin (seeded) can read dashboard.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import PASSWORD, USER_A, USER_B, Report, login, new_id, req  # noqa: E402

ADMIN_LOGIN = os.environ.get("HALOZALO_ADMIN_LOGIN", "").strip()
ADMIN_PASSWORD = os.environ.get("HALOZALO_ADMIN_PASSWORD", PASSWORD).strip()


def run() -> Report:
    report = Report("dashboard_devices")
    print(f"\n=== Agent {report.agent} ===")

    tok_a, _ = login(USER_A)
    tok_b, _ = login(USER_B)

    # features.md: system admin only — regular user forbidden
    status, stats = req("GET", "/stats", tok_a)
    report.ok(
        "GET /stats as USER → 403",
        status == 403,
        str(stats),
    )

    status, dash = req("GET", "/stats/dashboard", tok_a)
    report.ok(
        "GET /stats/dashboard as USER → 403",
        status == 403,
        str(dash),
    )

    if ADMIN_LOGIN:
        try:
            tok_admin, admin_user = login(ADMIN_LOGIN, ADMIN_PASSWORD)
            report.ok(
                "login platform admin",
                admin_user.get("platformRole") == "ADMIN",
                str(admin_user.get("platformRole")),
            )
            status, stats = req("GET", "/stats", tok_admin)
            report.ok(
                "GET /stats as ADMIN",
                status == 200
                and all(k in stats for k in ("logins", "groupsCreated", "messagesCreated", "mediaUploads")),
                str(stats),
            )
            status, dash = req("GET", "/stats/dashboard", tok_admin)
            report.ok(
                "GET /stats/dashboard as ADMIN",
                status == 200 and "activeUsers" in dash and "messagesByDay" in dash,
                f"activeUsers={dash.get('activeUsers')}",
            )
            # ban/unban self must fail; ban a throwaway if available — soft check on endpoint existence
            status, _ = req("POST", f"/admin/users/{admin_user['userId']}/ban", tok_admin)
            report.ok("admin cannot ban self (403/400)", status in (400, 403), str(status))
        except Exception as exc:  # noqa: BLE001
            report.ok("login platform admin", False, str(exc))
    else:
        report.ok(
            "HALOZALO_ADMIN_LOGIN not set — skip admin stats/ban",
            True,
            "set env to cover UC-ST-03..05",
        )

    status, activity = req("GET", "/me/activity", tok_a)
    report.ok(
        "GET /me/activity",
        status == 200 and activity.get("logins", 0) >= 1,
        str(activity),
    )

    status, activity_ranged = req("GET", "/me/activity?from=2020-01-01&to=2099-12-31", tok_a)
    report.ok(
        "GET /me/activity with from/to",
        status == 200 and "logins" in activity_ranged,
        str(activity_ranged),
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
