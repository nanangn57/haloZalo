#!/usr/bin/env python3
"""Agent: groups — create, add, transfer owner, dissolve, message (OWNER|MEMBER only)."""

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
    status, group = req("POST", "/groups", tok_a, {"name": name, "description": "e2e group"})
    report.ok("create group", status == 201 and group.get("groupId"), group.get("groupId", ""))
    gid = group["groupId"]
    report.ok("creator is OWNER", group.get("role") == "OWNER", str(group.get("role")))

    status, patched = req(
        "PATCH",
        f"/groups/{gid}",
        tok_a,
        {"name": name + " renamed", "description": "updated"},
    )
    report.ok(
        "owner patches group name/description",
        status == 200 and "renamed" in (patched.get("name") or ""),
        str(patched),
    )

    status, _ = req("POST", f"/groups/{gid}/members", tok_a, {"userId": uid_b})
    report.ok("add B as member (add thẳng)", status == 201)

    status, members = req("GET", f"/groups/{gid}/members", tok_a)
    by_id = {m.get("userId"): m for m in members.get("members", [])}
    report.ok("members include A and B", uid_a in by_id and uid_b in by_id, str(by_id.keys()))
    report.ok("roles are OWNER|MEMBER only", by_id[uid_a].get("role") == "OWNER" and by_id[uid_b].get("role") == "MEMBER")

    # Contract removed group ADMIN — endpoint must stay gone
    status, role = req("PUT", f"/groups/{gid}/members/{uid_b}/role", tok_a, {"role": "ADMIN"})
    report.ok("PUT …/role ADMIN is gone (404)", status == 404, str(role))

    status, transfer = req("POST", f"/groups/{gid}/transfer-owner", tok_a, {"userId": uid_b})
    report.ok(
        "transfer owner A→B",
        status == 200 and transfer.get("role") == "OWNER",
        str(transfer),
    )
    status, members = req("GET", f"/groups/{gid}/members", tok_a)
    by_id = {m.get("userId"): m for m in members.get("members", [])}
    report.ok(
        "B is OWNER after transfer",
        by_id.get(uid_b, {}).get("role") == "OWNER" and by_id.get(uid_a, {}).get("role") == "MEMBER",
        str(by_id),
    )

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
    types = [m.get("type") for m in page.get("messages", [])]
    report.ok("B sees group message", "group-agent-hi" in bodies, str(bodies))
    report.ok(
        "system messages present after membership changes",
        "SYSTEM" in types or any("owner" in (b or "").lower() or "added" in (b or "").lower() for b in bodies),
        str(list(zip(types, bodies))),
    )

    # A (now member) can leave
    status, _ = req("DELETE", f"/groups/{gid}/members/{uid_a}", tok_a)
    report.ok("former owner (now member) leaves", status == 204)

    status, members = req("GET", f"/groups/{gid}/members", tok_b)
    ids = {m.get("userId") for m in members.get("members", [])}
    report.ok("A removed; B remains", uid_a not in ids and uid_b in ids, str(ids))

    # Dissolve as current owner B
    status, _ = req("DELETE", f"/groups/{gid}", tok_b)
    report.ok("owner dissolves group", status == 204)

    status, groups = req("GET", "/groups", tok_b)
    report.ok(
        "dissolved group gone from list",
        not any(g.get("groupId") == gid for g in groups.get("groups", [])),
    )

    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
