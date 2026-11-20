#!/usr/bin/env python3
"""Agent: media — upload IMAGE/DOCUMENT, send as message, fan-out, public GET, history."""

from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib import TINY_PNG, USER_A, USER_B, Report, login, new_id, req, ws_url  # noqa: E402


async def _fanout(report: Report, tok_a: str, tok_b: str, cid: str, media_url: str) -> None:
    import websockets

    ev_b: list[dict] = []
    ev_a2: list[dict] = []
    a_other = await websockets.connect(ws_url(tok_a), open_timeout=5)
    b = await websockets.connect(ws_url(tok_b), open_timeout=5)
    try:

        async def drain(ws, bucket):
            try:
                while True:
                    bucket.append(json.loads(await ws.recv()))
            except Exception:
                return

        t1 = asyncio.create_task(drain(a_other, ev_a2))
        t2 = asyncio.create_task(drain(b, ev_b))
        await asyncio.sleep(0.35)

        status, img = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_a,
            {
                "type": "IMAGE",
                "body": media_url,
                "clientMsgId": new_id(),
                "caption": "agent-media",
            },
        )
        report.ok("send IMAGE message", status == 201 and img.get("type") == "IMAGE", img.get("type", ""))
        await asyncio.sleep(0.9)

        def images(bucket):
            return [
                e
                for e in bucket
                if e.get("type") == "messaging.message.created"
                and (e.get("payload") or {}).get("type") == "IMAGE"
            ]

        report.ok("IMAGE fan-out to peer B", len(images(ev_b)) >= 1)
        report.ok("IMAGE fan-out to A's other device", len(images(ev_a2)) >= 1)

        for task in (t1, t2):
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
    finally:
        await a_other.close()
        await b.close()


def run() -> Report:
    report = Report("media")
    print(f"\n=== Agent {report.agent} ===")

    tok_a, user_a = login(USER_A)
    tok_b, user_b = login(USER_B)
    status, direct = req("POST", "/conversations/direct", tok_a, {"userId": user_b["userId"]})
    report.ok("open DIRECT for media", status in (200, 201))
    cid = direct["conversationId"]

    status, up = req(
        "POST",
        "/media/uploads",
        tok_a,
        form={
            "file": ("dot.png", TINY_PNG, "image/png"),
            "type": "IMAGE",
            "conversationId": cid,
        },
    )
    report.ok("upload IMAGE", status == 200 and "url" in up, up.get("url", ""))
    media_url = up.get("url", "")

    if media_url and "/media/" in media_url:
        media_path = "/media/" + media_url.rsplit("/media/", 1)[-1]
        status, raw = req("GET", media_path, raw=True)
        report.ok("GET /media/{id} public bytes", status == 200 and isinstance(raw, (bytes, bytearray)) and raw[:8] == TINY_PNG[:8])
    else:
        report.ok("GET /media/{id} public bytes", False, "missing url")

    # document upload
    status, doc = req(
        "POST",
        "/media/uploads",
        tok_a,
        form={
            "file": ("note.txt", b"hello agent doc", "text/plain"),
            "type": "DOCUMENT",
            "conversationId": cid,
        },
    )
    report.ok("upload DOCUMENT", status == 200 and doc.get("type") == "DOCUMENT", doc.get("url", ""))

    if doc.get("url"):
        status, msg = req(
            "POST",
            f"/conversations/{cid}/messages",
            tok_a,
            {
                "type": "DOCUMENT",
                "body": doc["url"],
                "clientMsgId": new_id(),
                "fileName": "note.txt",
                "mimeType": "text/plain",
                "size": len(b"hello agent doc"),
            },
        )
        report.ok("send DOCUMENT message", status == 201 and msg.get("type") == "DOCUMENT")

    if media_url:
        asyncio.get_event_loop().run_until_complete(_fanout(report, tok_a, tok_b, cid, media_url))

    status, page = req("GET", f"/conversations/{cid}/messages", tok_b)
    types = [m.get("type") for m in page.get("messages", [])]
    report.ok("history contains IMAGE", "IMAGE" in types, str(types[-5:]))
    report.ok("history contains DOCUMENT", "DOCUMENT" in types, str(types[-5:]))

    return report


if __name__ == "__main__":
    result = run()
    print(result.summary())
    raise SystemExit(1 if result.failed else 0)
