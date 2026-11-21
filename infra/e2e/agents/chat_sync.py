#!/usr/bin/env python3
"""Agent: chat + multi-device realtime sync (1 user, 2 sockets = web+mobile)."""

from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import USER_A, USER_B, Report, login, new_id, req, ws_url  # noqa: E402


async def _ws_suite(report: Report, tok_a: str, tok_b: str, cid: str) -> None:
    import websockets

    ev_web: list[dict] = []
    ev_mobile: list[dict] = []
    ev_b: list[dict] = []

    web = await websockets.connect(ws_url(tok_a), open_timeout=5)
    mobile = await websockets.connect(ws_url(tok_a), open_timeout=5)
    peer = await websockets.connect(ws_url(tok_b), open_timeout=5)
    try:

        async def drain(ws, bucket):
            try:
                while True:
                    bucket.append(json.loads(await ws.recv()))
            except Exception:
                return

        tasks = [
            asyncio.create_task(drain(web, ev_web)),
            asyncio.create_task(drain(mobile, ev_mobile)),
            asyncio.create_task(drain(peer, ev_b)),
        ]
        await asyncio.sleep(0.35)

        # Peer sends TEXT → both A devices
        status, msg = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_b,
            {"type": "TEXT", "body": "sync-agent-text", "clientMsgId": new_id()},
        )
        report.ok("B sends TEXT", status == 201, f"seq={msg.get('seq')}")
        seq = msg.get("seq", 0)
        await asyncio.sleep(0.8)
        report.ok(
            "A-web gets message.created",
            any(e.get("type") == "messaging.message.created" for e in ev_web),
        )
        report.ok(
            "A-mobile gets message.created",
            any(e.get("type") == "messaging.message.created" for e in ev_mobile),
        )

        # A marks read → both A devices, not B (count only new events)
        n_web, n_mobile, n_b = len(ev_web), len(ev_mobile), len(ev_b)
        status, read = req("PUT", f"/conversations/{cid}/read", tok_a, {"seq": seq})
        report.ok("A mark-read", status == 200 and read.get("unreadCount") == 0, str(read))
        await asyncio.sleep(0.8)
        read_web = [e for e in ev_web[n_web:] if e.get("type") == "messaging.read.updated"]
        read_mobile = [e for e in ev_mobile[n_mobile:] if e.get("type") == "messaging.read.updated"]
        read_b = [e for e in ev_b[n_b:] if e.get("type") == "messaging.read.updated"]
        report.ok("read.updated → A-web", len(read_web) >= 1)
        report.ok("read.updated → A-mobile", len(read_mobile) >= 1)
        report.ok("read.updated NOT to B", len(read_b) == 0, f"B got {len(read_b)}")

        # Typing / presence from A-web → B
        n_b = len(ev_b)
        await web.send(
            json.dumps(
                {
                    "type": "messaging.presence",
                    "payload": {"conversationId": cid, "status": "online"},
                }
            )
        )
        await web.send(
            json.dumps(
                {
                    "type": "messaging.typing",
                    "payload": {"conversationId": cid, "typing": True},
                }
            )
        )
        await asyncio.sleep(0.8)
        types_b = {e.get("type") for e in ev_b[n_b:]}
        report.ok("B receives presence", "messaging.presence" in types_b, str(types_b))
        report.ok("B receives typing", "messaging.typing" in types_b, str(types_b))

        # Idempotent clientMsgId
        client = new_id()
        status, first = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_a,
            {"type": "TEXT", "body": "idempotent", "clientMsgId": client},
        )
        status2, second = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_a,
            {"type": "TEXT", "body": "idempotent", "clientMsgId": client},
        )
        report.ok(
            "clientMsgId idempotent",
            status in (200, 201)
            and status2 in (200, 201)
            and first.get("messageId") == second.get("messageId"),
            f"{first.get('messageId')} / {second.get('messageId')}",
        )

        for task in tasks:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
    finally:
        await web.close()
        await mobile.close()
        await peer.close()


def run() -> Report:
    report = Report("chat_sync")
    print(f"\n=== Agent {report.agent} ===")

    tok_a, user_a = login(USER_A)
    tok_b, user_b = login(USER_B)
    status, direct = req("POST", "/conversations/direct", tok_a, {"userId": user_b["userId"]})
    report.ok("open DIRECT", status in (200, 201), direct.get("conversationId", ""))
    cid = direct["conversationId"]

    status, page = req("GET", f"/conversations/{cid}/messages", tok_a)
    report.ok("load history", status == 200 and "messages" in page)

    status, emo = req(
        "POST",
        f"/conversations/{cid}/messages",
        tok_b,
        {"type": "EMOTION", "body": "haha", "clientMsgId": new_id()},
    )
    report.ok("send EMOTION", status == 201 and emo.get("type") == "EMOTION")

    asyncio.run(_ws_suite(report, tok_a, tok_b, cid))
    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
