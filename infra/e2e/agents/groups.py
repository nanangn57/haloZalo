#!/usr/bin/env python3
"""Agent: groups — create, add member, open conversation, message, leave/remove."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import USER_A, USER_B, Report, login, new_id, req  # noqa: E402


def run() -> Report:
    report = Report("groups")
    print(f"\n=== Agent {report.agent} ===")

    tok_a, user_a = login(USER_A)
    tok_b, user_b = login(USER_B)
    uid_a, uid_b = user_a["userId"], user_b["userId"]

    name = f"Agent Group {new_id()[:8]}"
    status, group = req("POST", "/groups", tok_a, {"name": name})
    report.ok("create group", status == 201 and group.get("groupId"), group.get("groupId", ""))
    gid = group["groupId"]

    status, _ = req("POST", f"/groups/{gid}/members", tok_a, {"userId": uid_b})
    report.ok("add B as member", status == 201)

    status, members = req("GET", f"/groups/{gid}/members", tok_a)
    ids = {m.get("userId") for m in members.get("members", [])}
    report.ok("members include A and B", uid_a in ids and uid_b in ids, str(ids))

    status, role = req("PUT", f"/groups/{gid}/members/{uid_b}/role", tok_a, {"role": "ADMIN"})
    report.ok("A promotes B to ADMIN", status == 200 and role.get("role") == "ADMIN", str(role))

    status, members = req("GET", f"/groups/{gid}/members", tok_a)
    b_role = next((m.get("role") for m in members.get("members", []) if m.get("userId") == uid_b), None)
    report.ok("B role is ADMIN", b_role == "ADMIN", str(b_role))

    status, role = req("PUT", f"/groups/{gid}/members/{uid_b}/role", tok_a, {"role": "MEMBER"})
    report.ok("A demotes B to MEMBER", status == 200 and role.get("role") == "MEMBER", str(role))

    status, gconv = req("POST", "/conversations/group", tok_a, {"groupId": gid})
    report.ok("open GROUP conversation", status in (200, 201), gconv.get("conversationId", ""))
    report.ok("conversationId == groupId", gconv.get("conversationId") == gid)

    status, msg = req(
        "POST",
        f"/conversations/{gid}/messages",
        tok_a,
        {"type": "TEXT", "body": "group-agent-hi", "clientMsgId": new_id()},
    )
    report.ok("group TEXT", status == 201, f"seq={msg.get('seq')}")

    status, page = req("GET", f"/conversations/{gid}/messages", tok_b)
    bodies = [m.get("body") for m in page.get("messages", [])]
    report.ok("B sees group message without manual membership sync", "group-agent-hi" in bodies, str(bodies))

    status, _ = req("DELETE", f"/groups/{gid}/members/{uid_b}", tok_b)
    report.ok("B leaves group", status == 204)

    status, members = req("GET", f"/groups/{gid}/members", tok_a)
    ids = {m.get("userId") for m in members.get("members", [])}
    report.ok("B removed from members", uid_b not in ids and uid_a in ids, str(ids))

    status, groups = req("GET", "/groups", tok_a)
    report.ok("A still lists group", any(g.get("groupId") == gid for g in groups.get("groups", [])))

    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
