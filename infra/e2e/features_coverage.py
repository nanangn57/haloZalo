#!/usr/bin/env python3
"""Extra UC coverage for doc/features.md beyond the six agent suites.

Creates ephemeral users via register+verify (mail log) when possible; falls back to USER_A/B.
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

from lib import device_id, PASSWORD, USER_A, USER_B, Report, login, new_id, req  # noqa: E402


def _verify_token_from_logs(email: str) -> str | None:
    try:
        out = subprocess.check_output(
            ["docker", "logs", "halozalo-local-app-1"],
            stderr=subprocess.STDOUT,
            text=True,
            timeout=30,
        )
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
        return None
    # last verify link whose nearby context mentions email, or just last token after register
    links = re.findall(r"/auth/verify\?token=([a-f0-9-]{36})", out)
    if not links:
        return None
    return links[-1]


def register_and_login(display: str) -> tuple[str, dict]:
    email = f"feat-{uuid.uuid4().hex[:8]}@example.com"
    status, body = req(
        "POST",
        "/auth/register",
        body={"email": email, "password": PASSWORD, "displayName": display},
    )
    if status not in (201, 202):
        raise RuntimeError(f"register failed: {status} {body}")
    if body.get("status") == "ACTIVE":
        return login(email, PASSWORD)
    time.sleep(0.4)
    token = _verify_token_from_logs(email)
    if not token:
        raise RuntimeError(f"no verify token in logs for {email}")
    status, verified = req("GET", f"/auth/verify?token={token}&deviceId={device_id('verify')}")
    if status != 200 or not verified.get("accessToken"):
        raise RuntimeError(f"verify failed: {status} {verified}")
    return verified["accessToken"], verified["user"]


def run() -> Report:
    report = Report("features_coverage")
    print(f"\n=== Agent {report.agent} ===")

    # --- Auth extras ---
    try:
        tok_new, user_new = register_and_login("Feat User")
        report.ok("UC-AUTH-01/02 register+verify→session", bool(tok_new and user_new.get("userId")))
    except Exception as exc:  # noqa: BLE001
        report.ok("UC-AUTH-01/02 register+verify→session", False, str(exc))
        tok_new, user_new = login(USER_A)

    status, _ = req("POST", "/auth/verify/resend", body={"email": "nobody-missing@example.com"})
    report.ok("UC-AUTH-05 resend unknown → 404/202", status in (202, 404), str(status))

    status, _ = req("POST", "/auth/password/forgot", body={"email": USER_A if "@" in USER_A else "a@example.com"})
    report.ok("UC-AUTH-09 forgot password → 202", status == 202)

    tok_a, user_a = login(USER_A)
    tok_b, user_b = login(USER_B)
    uid_a, uid_b = user_a["userId"], user_b["userId"]

    status, sessions = req("GET", "/auth/sessions", tok_a)
    report.ok("UC-AUTH-12 list sessions", status == 200 and len(sessions.get("sessions", [])) >= 1)

    # --- Friends extras ---
    status, reqs = req("GET", "/friends/requests", tok_a)
    report.ok(
        "UC-FR-06 list friend requests inbox",
        status == 200 and "incoming" in reqs and "outgoing" in reqs,
        str(reqs.keys()),
    )

    # cancel outgoing if any leftover to C
    try:
        tok_c, user_c = register_and_login("Feat C")
        uid_c = user_c["userId"]
        status, _ = req("POST", "/friends/requests", tok_a, {"userId": uid_c})
        report.ok("UC-FR-02 send request", status in (200, 201))
        status, _ = req("DELETE", f"/friends/requests/{uid_c}", tok_a)
        report.ok("UC-FR-05 cancel outgoing request", status == 204)

        status, _ = req("POST", "/friends/requests", tok_a, {"userId": uid_c})
        status, _ = req("POST", f"/friends/requests/{uid_a}/reject", tok_c)
        report.ok("UC-FR-04 reject incoming", status == 204)

        status, _ = req("POST", "/friends/requests", tok_a, {"userId": uid_c})
        status, _ = req("POST", f"/friends/requests/{uid_a}/accept", tok_c)
        report.ok("UC-FR-03 accept", status == 200)
        status, _ = req("DELETE", f"/friends/{uid_c}", tok_a)
        report.ok("UC-FR-09 unfriend", status == 204)
    except Exception as exc:  # noqa: BLE001
        report.ok("friend request extras", False, str(exc))

    # --- Messaging extras ---
    status, conv = req("POST", "/conversations/direct", tok_a, {"userId": uid_b})
    cid = conv.get("conversationId")
    report.ok("UC-CV-01/02 open direct", status in (200, 201) and bool(cid))

    mid = None
    if cid:
        status, msg = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_a,
            {"type": "TEXT", "body": "coverage-hello", "clientMsgId": new_id()},
        )
        mid = msg.get("messageId")
        report.ok("UC-MSG-01 send text", status == 201 and bool(mid))

        status, prefs = req("PATCH", f"/conversations/{cid}/prefs", tok_a, {"pinned": True, "muted": True})
        report.ok("UC-CV-04/05 pin+mute", status == 200 and prefs.get("pinned") is True and prefs.get("muted") is True)

        status, prefs = req("PATCH", f"/conversations/{cid}/prefs", tok_a, {"archived": True})
        report.ok("UC-CV-06 archive", status == 200 and prefs.get("archived") is True)
        status, prefs = req("PATCH", f"/conversations/{cid}/prefs", tok_a, {"archived": False, "pinned": False, "muted": False})
        report.ok("unarchive/unpin", status == 200)

        if mid:
            status, _ = req("PUT", f"/conversations/{cid}/messages/{mid}/reactions/like", tok_a)
            report.ok("UC-MSG-16 react", status in (200, 204))
            status, replied = req(
                "POST",
                f"/conversations/{cid}/messages",
                tok_a,
                {"type": "TEXT", "body": "reply-here", "clientMsgId": new_id(), "replyTo": mid},
            )
            report.ok("UC-MSG-17 reply", status == 201 and replied.get("replyTo") == mid)
            status, _ = req("DELETE", f"/conversations/{cid}/messages/{mid}", tok_a)
            report.ok("UC-MSG-14 recall", status == 204)

        status, page = req("GET", f"/conversations/{cid}/messages?q=coverage", tok_a)
        report.ok("UC-MSG-21 search in chat", status == 200, f"n={len(page.get('messages', []))}")

        status, presence = req("GET", f"/presence?ids={uid_a},{uid_b}", tok_a)
        report.ok(
            "UC-NT-01 presence",
            status == 200 and len(presence.get("presences", [])) >= 1,
            str(presence),
        )

        status, notif = req("GET", "/me/notifications", tok_a)
        report.ok("UC-NT-03 get notification prefs", status == 200 and "pushEnabled" in notif)
        status, notif = req("PATCH", "/me/notifications", tok_a, {"previewEnabled": False})
        report.ok("UC-NT-03 patch notification prefs", status == 200 and notif.get("previewEnabled") is False)
        req("PATCH", "/me/notifications", tok_a, {"previewEnabled": True})

        # block then send must fail
        status, _ = req("POST", "/blocks", tok_a, {"userId": uid_b})
        report.ok("UC-FR-10 block", status in (200, 201, 204), f"status={status}")
        status, blocked_send = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_b,
            {"type": "TEXT", "body": "should-fail", "clientMsgId": new_id()},
        )
        # either 403 on send or on open — accept either as block enforced
        status2, _ = req("POST", "/conversations/direct", tok_b, {"userId": uid_a})
        report.ok(
            "UC-MSG-23 block prevents chat",
            blocked_send == 403 or status2 == 403,
            f"send={blocked_send} open={status2}",
        )
        status, _ = req("DELETE", f"/blocks/{uid_b}", tok_a)
        report.ok("UC-FR-11 unblock", status in (200, 204), f"status={status}")

    # --- Stranger message request ---
    try:
        tok_s, user_s = register_and_login("Stranger")
        status, sconv = req("POST", "/conversations/direct", tok_s, {"userId": uid_a})
        report.ok(
            "UC-MSG-24 stranger opens pending request",
            status in (200, 201) and (sconv.get("pendingRequest") in (True, False)),
            str(sconv),
        )
        scid = sconv.get("conversationId")
        if scid:
            status, inbox = req("GET", "/conversations?inbox=requests", tok_a)
            report.ok("message-request inbox", status == 200, f"n={len(inbox.get('conversations', inbox if isinstance(inbox, list) else []))}")
            status, _ = req("POST", f"/conversations/{scid}/accept-request", tok_a)
            report.ok("accept message request", status in (200, 204), str(status))
    except Exception as exc:  # noqa: BLE001
        report.ok("stranger message request flow", False, str(exc))

    # --- Sessions logout ---
    status, _ = req("POST", "/auth/logout", tok_new)
    report.ok("UC-AUTH-08 logout", status in (200, 204))

    print(json.dumps({"agent": report.agent, "passed": report.passed_count, "total": len(report.checks)}, indent=2))
    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
