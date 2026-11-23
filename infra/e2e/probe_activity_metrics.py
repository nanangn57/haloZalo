#!/usr/bin/env python3
"""Fresh-user probe: does /me/activity capture logins / sent / received / active chats?

Creates two ephemeral users, exercises login×2, DM, group message, then asserts KPIs.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from lib import device_id, PASSWORD, login, new_id, req  # noqa: E402


def verify_token_from_logs() -> str | None:
    try:
        out = subprocess.check_output(
            ["docker", "logs", "halozalo-local-app-1"],
            stderr=subprocess.STDOUT,
            text=True,
            timeout=60,
        )
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired) as exc:
        print(f"docker logs failed: {exc}")
        return None
    links = re.findall(r"/auth/verify\?token=([a-f0-9-]{36})", out)
    return links[-1] if links else None


def register_and_get_session(display: str) -> tuple[str, dict, str]:
    email = f"probe-{uuid.uuid4().hex[:10]}@example.com"
    status, body = req(
        "POST",
        "/auth/register",
        body={"email": email, "password": PASSWORD, "displayName": display},
    )
    if status not in (201, 202):
        raise RuntimeError(f"register {display}: {status} {body}")
    if body.get("status") == "ACTIVE":
        token, user = login(email, PASSWORD)
        return token, user, email
    time.sleep(0.5)
    token_verify = verify_token_from_logs()
    if not token_verify:
        raise RuntimeError(f"no verify token for {email}")
    status, verified = req("GET", f"/auth/verify?token={token_verify}&deviceId={device_id('verify')}")
    if status != 200 or not verified.get("accessToken"):
        raise RuntimeError(f"verify {email}: {status} {verified}")
    return verified["accessToken"], verified["user"], email


def activity(token: str) -> dict:
    status, body = req("GET", "/me/activity?from=2020-01-01&to=2099-12-31", token)
    if status != 200:
        raise RuntimeError(f"/me/activity {status} {body}")
    return body


def sessions(token: str) -> list:
    status, body = req("GET", "/auth/sessions", token)
    if status != 200:
        raise RuntimeError(f"/auth/sessions {status} {body}")
    return body.get("sessions", [])


def main() -> int:
    print("=== probe_activity_metrics ===\n")

    print("1) Register+verify Alice (auto-login on verify counts as logged_in)")
    tok_a, user_a, email_a = register_and_get_session("Probe Alice")
    uid_a = user_a["userId"]
    print(f"   Alice {email_a} uid={uid_a}")
    time.sleep(0.3)
    act0 = activity(tok_a)
    print(f"   after verify: logins={act0.get('logins')} sessions={len(sessions(tok_a))}")

    print("2) Explicit login Alice again (2nd session / 2nd login event)")
    tok_a2, _ = login(email_a, PASSWORD)
    time.sleep(0.3)
    act1 = activity(tok_a2)
    sess_a = sessions(tok_a2)
    print(f"   after 2nd login: logins={act1.get('logins')} sessions={len(sess_a)}")

    print("3) Register+verify Bob")
    tok_b, user_b, email_b = register_and_get_session("Probe Bob")
    uid_b = user_b["userId"]
    print(f"   Bob {email_b} uid={uid_b}")

    print("4) Friend A→B accept")
    status, _ = req("POST", "/friends/requests", tok_a2, {"userId": uid_b})
    print(f"   request status={status}")
    status, _ = req("POST", f"/friends/requests/{uid_a}/accept", tok_b)
    print(f"   accept status={status}")

    print("5) Direct conversation: A sends 1, B sends 1")
    status, direct = req("POST", "/conversations/direct", tok_a2, {"userId": uid_b})
    if status not in (200, 201):
        raise RuntimeError(f"direct conv {status} {direct}")
    cid = direct["conversationId"]
    print(f"   direct={cid}")
    status, msg_a = req(
        "POST",
        f"/conversations/{cid}/messages",
        tok_a2,
        {"type": "TEXT", "body": "hi from alice", "clientMsgId": new_id()},
    )
    print(f"   A send status={status} seq={msg_a.get('seq')}")
    status, msg_b = req(
        "POST",
        f"/conversations/{cid}/messages",
        tok_b,
        {"type": "TEXT", "body": "hi from bob", "clientMsgId": new_id()},
    )
    print(f"   B send status={status} seq={msg_b.get('seq')}")

    print("6) Group: A creates, adds B, A sends 1 in group")
    status, group = req(
        "POST",
        "/groups",
        tok_a2,
        {"name": f"Probe Group {new_id()[:6]}", "description": "activity probe"},
    )
    if status != 201:
        raise RuntimeError(f"create group {status} {group}")
    gid = group["groupId"]
    status, _ = req("POST", f"/groups/{gid}/members", tok_a2, {"userId": uid_b})
    print(f"   group={gid} add B status={status}")
    status, gconv = req("POST", "/conversations/group", tok_a2, {"groupId": gid})
    print(f"   open group conv status={status}")
    status, gmsg = req(
        "POST",
        f"/conversations/{gid}/messages",
        tok_a2,
        {"type": "TEXT", "body": "group hello", "clientMsgId": new_id()},
    )
    print(f"   A group send status={status} seq={gmsg.get('seq')}")

    # Allow analytics consumer to catch up
    time.sleep(1.0)

    print("\n7) Final /me/activity for Alice")
    final = activity(tok_a2)
    sess_final = sessions(tok_a2)
    print(json.dumps({
        "logins": final.get("logins"),
        "messagesSent": final.get("messagesSent"),
        "messagesReceived": final.get("messagesReceived"),
        "activeConversations": final.get("activeConversations"),
        "groupsCreated": final.get("groupsCreated"),
        "friends.count": (final.get("friends") or {}).get("count"),
        "messagesByDay": final.get("messagesByDay"),
        "directVsGroup": final.get("directVsGroup"),
        "mediaVsText": final.get("mediaVsText"),
        "topConversations": final.get("topConversations"),
        "liveSessions": len(sess_final),
    }, indent=2))

    # Expectations for a clean capture after this probe
    expected = {
        "logins>=": 2,  # verify auto-login + explicit login
        "messagesSent>=": 2,  # 1 direct + 1 group (system msgs may not count as Alice send)
        "messagesReceived>=": 1,  # Bob's DM
        "activeConversations>=": 2,  # direct + group if Alice sent in both
        "groupsCreated>=": 1,
        "liveSessions>=": 2,
    }

    checks = []
    checks.append(("logins >= 2 (verify+login)", (final.get("logins") or 0) >= 2, final.get("logins")))
    checks.append(("messagesSent >= 2 (DM+group)", (final.get("messagesSent") or 0) >= 2, final.get("messagesSent")))
    checks.append(("messagesReceived >= 1 (Bob→Alice)", (final.get("messagesReceived") or 0) >= 1, final.get("messagesReceived")))
    checks.append(
        (
            "activeConversations >= 2 (DM+group Alice sent)",
            (final.get("activeConversations") or 0) >= 2,
            final.get("activeConversations"),
        )
    )
    checks.append(("groupsCreated >= 1", (final.get("groupsCreated") or 0) >= 1, final.get("groupsCreated")))
    checks.append(("live sessions >= 2", len(sess_final) >= 2, len(sess_final)))
    # User hypothesis: live sessions should equal logins (or logins >= sessions)
    checks.append(
        (
            "logins >= live sessions (hypothesis)",
            (final.get("logins") or 0) >= len(sess_final),
            f"logins={final.get('logins')} sessions={len(sess_final)}",
        )
    )

    print("\n=== ASSERTIONS ===")
    failed = 0
    for name, ok, detail in checks:
        mark = "PASS" if ok else "FAIL"
        if not ok:
            failed += 1
        print(f"  [{mark}] {name} → {detail}")

    print(f"\nexpected guide: {expected}")
    print(f"result: {'ALL PASS' if failed == 0 else f'{failed} FAILED'}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
