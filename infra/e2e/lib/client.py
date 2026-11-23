"""Shared HTTP/WS helpers for haloZalo E2E suites."""

from __future__ import annotations

import json
import os
import uuid
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any


BASE = os.environ.get("HALOZALO_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
WS_BASE = os.environ.get("HALOZALO_WS_URL", BASE.replace("http://", "ws://").replace("https://", "wss://"))
USER_A = os.environ.get("HALOZALO_USER_A", "ngoc_anh")
USER_B = os.environ.get("HALOZALO_USER_B", "huy")
PASSWORD = os.environ.get("HALOZALO_PASSWORD", "password1")

# Seed usernames → email (login chỉ nhận email).
_LOGIN_EMAIL = {
    "ngoc_anh": "anh@example.com",
    "huy": "huy@example.com",
}


def login_email(login: str) -> str:
    if "@" in login:
        return login
    return _LOGIN_EMAIL.get(login, f"{login}@example.com")


def device_id(suffix: str = "e2e") -> str:
    """deviceId bền theo process+suffix — khớp pattern Identity."""
    return f"e2e-{suffix}-" + uuid.uuid4().hex[:16]




@dataclass
class Check:
    name: str
    passed: bool
    detail: str = ""


@dataclass
class Report:
    agent: str
    checks: list[Check] = field(default_factory=list)

    def ok(self, name: str, cond: bool, detail: str = "") -> bool:
        self.checks.append(Check(name, bool(cond), detail))
        mark = "PASS" if cond else "FAIL"
        print(f"  [{'✓' if cond else '✗'}] {mark}: {name}" + (f" — {detail}" if detail else ""))
        return bool(cond)

    @property
    def failed(self) -> list[Check]:
        return [c for c in self.checks if not c.passed]

    @property
    def passed_count(self) -> int:
        return sum(1 for c in self.checks if c.passed)

    def summary(self) -> str:
        return f"{self.agent}: {self.passed_count}/{len(self.checks)} passed"


def req(
    method: str,
    path: str,
    token: str | None = None,
    body: Any = None,
    form: dict[str, Any] | None = None,
    raw: bool = False,
) -> tuple[int, Any]:
    data = None
    headers: dict[str, str] = {}
    if form is not None:
        boundary = "----e2e" + uuid.uuid4().hex
        parts: list[bytes] = []
        for name, value in form.items():
            if isinstance(value, tuple):
                filename, content, ctype = value
                parts.append(
                    (
                        f"--{boundary}\r\n"
                        f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                        f"Content-Type: {ctype}\r\n\r\n"
                    ).encode()
                    + content
                    + b"\r\n"
                )
            else:
                parts.append(
                    (
                        f"--{boundary}\r\n"
                        f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
                        f"{value}\r\n"
                    ).encode()
                )
        parts.append(f"--{boundary}--\r\n".encode())
        data = b"".join(parts)
        headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as resp:
            payload = resp.read()
            if raw:
                return resp.status, payload
            if not payload:
                return resp.status, {}
            return resp.status, json.loads(payload.decode())
    except urllib.error.HTTPError as err:
        payload = err.read()
        if raw:
            return err.code, payload
        text = payload.decode(errors="replace")
        try:
            return err.code, json.loads(text) if text else {}
        except json.JSONDecodeError:
            return err.code, {"raw": text}


def login(
    username: str = USER_A,
    password: str = PASSWORD,
    *,
    device: str | None = None,
    device_label: str = "e2e",
) -> tuple[str, dict[str, Any]]:
    email = login_email(username)
    body = {
        "login": email,
        "password": password,
        "deviceId": device or device_id(email.split("@")[0]),
        "deviceLabel": device_label,
    }
    status, resp = req("POST", "/auth/login", body=body)
    if status != 200:
        raise RuntimeError(f"login failed for {email}: {status} {resp}")
    return resp["accessToken"], resp["user"]


def ws_url(token: str) -> str:
    return f"{WS_BASE}/ws?access_token={urllib.parse.quote(token)}"


def new_id() -> str:
    return str(uuid.uuid4())


TINY_PNG = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde"
    b"\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82"
)
