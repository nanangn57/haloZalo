#!/usr/bin/env python3
"""Orchestrator: run all haloZalo E2E suites against the live monolith."""

from __future__ import annotations

import importlib
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from lib import BASE  # noqa: E402

SUITES = [
    "agents.auth_profile",
    "agents.friends",
    "agents.chat_sync",
    "agents.media",
    "agents.groups",
    "agents.dashboard_devices",
    "features_coverage",
]


def main() -> int:
    print(f"haloZalo E2E → {BASE}")
    started = time.time()
    reports = []
    failed_suites = []
    os_environ_admin = False

    # Platform admin for dashboard / ban coverage
    import os

    if not os.environ.get("HALOZALO_ADMIN_LOGIN"):
        os.environ["HALOZALO_ADMIN_LOGIN"] = "admin@example.com"
        os.environ.setdefault("HALOZALO_ADMIN_PASSWORD", "password1")
        os_environ_admin = True

    for module_name in SUITES:
        module = importlib.import_module(module_name)
        report = module.run()
        reports.append(report)
        print(report.summary())
        if report.failed:
            failed_suites.append(report.agent)

    # Full features.md UC matrix (API + web/mobile presence)
    import uc_matrix

    matrix = uc_matrix.run()
    matrix_path = ROOT / "uc-matrix-report.json"
    matrix_path.write_text(json.dumps(matrix, indent=2), encoding="utf-8")
    if matrix["api"]["FAIL"]:
        failed_suites.append("uc_matrix")

    elapsed = time.time() - started
    total_checks = sum(len(r.checks) for r in reports)
    total_pass = sum(r.passed_count for r in reports)

    summary = {
        "base": BASE,
        "elapsedSec": round(elapsed, 2),
        "suites": len(reports) + 1,
        "checksPassed": total_pass,
        "checksTotal": total_checks,
        "failedSuites": failed_suites,
        "adminEnvInjected": os_environ_admin,
        "ucMatrix": {
            "totalUCs": matrix["totalUCs"],
            "api": matrix["api"],
            "web": matrix["web"],
            "mobile": matrix["mobile"],
            "report": str(matrix_path),
        },
        "perSuite": [
            {
                "suite": r.agent,
                "passed": r.passed_count,
                "total": len(r.checks),
                "failures": [{"name": c.name, "detail": c.detail} for c in r.failed],
            }
            for r in reports
        ],
    }

    out = ROOT / "last-report.json"
    out.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(f"\n=== TOTAL {total_pass}/{total_checks} agent checks, {len(failed_suites)} suite(s) failed, {elapsed:.1f}s ===")
    print(
        f"UC matrix: API {matrix['api']} | WEB {matrix['web']} | MOBILE {matrix['mobile']}"
    )
    print(f"Report: {out}")
    print(f"UC matrix: {matrix_path}")
    if failed_suites:
        print("Failed:", ", ".join(failed_suites))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
