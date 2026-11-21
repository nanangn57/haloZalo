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
]


def main() -> int:
    print(f"haloZalo E2E → {BASE}")
    started = time.time()
    reports = []
    failed_suites = []

    for module_name in SUITES:
        module = importlib.import_module(module_name)
        report = module.run()
        reports.append(report)
        print(report.summary())
        if report.failed:
            failed_suites.append(report.agent)

    elapsed = time.time() - started
    total_checks = sum(len(r.checks) for r in reports)
    total_pass = sum(r.passed_count for r in reports)

    summary = {
        "base": BASE,
        "elapsedSec": round(elapsed, 2),
        "suites": len(reports),
        "checksPassed": total_pass,
        "checksTotal": total_checks,
        "failedSuites": failed_suites,
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
    print(f"\n=== TOTAL {total_pass}/{total_checks} checks, {len(failed_suites)} suite(s) failed, {elapsed:.1f}s ===")
    print(f"Report: {out}")
    if failed_suites:
        print("Failed:", ", ".join(failed_suites))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
