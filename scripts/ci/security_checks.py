#!/usr/bin/env python3
"""Fail-closed checks for obvious committed credentials and accidental CD triggers.

Not a substitute for GitHub secret scanning or a comprehensive security audit.
Never prints the value of a suspected secret.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = (
    ("GitHub token", re.compile(rb"(?:gh[pousr]_[A-Za-z0-9_]{30,}|github_pat_[A-Za-z0-9_]{40,})")),
    ("API secret key", re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{32,}")),
    ("private key header", re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
    ("Render deploy hook", re.compile(rb"https://api[.]render[.]com/deploy/srv-[A-Za-z0-9]+[?]key=[A-Za-z0-9_-]+")),
)

def tracked_paths() -> list[Path]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [ROOT / p.decode("utf-8", "surrogateescape") for p in raw.split(b"\x00") if p]

def inspect_sources() -> list[str]:
    findings: list[str] = []
    for path in tracked_paths():
        if not path.is_file() or path.stat().st_size > 2_000_000:
            continue
        data = path.read_bytes()
        if b"\x00" in data[:4096]:
            continue
        for label, pattern in PATTERNS:
            for match in pattern.finditer(data):
                line = data.count(b"\n", 0, match.start()) + 1
                findings.append(f"{path.relative_to(ROOT)}:{line}: {label} detected")
    return findings

def verify_cd_policy() -> list[str]:
    errors: list[str] = []
    cd = (ROOT / ".github/workflows/cd.yml").read_text(encoding="utf-8")
    if not re.search(r"(?m)^on:\s*\n\s+workflow_dispatch:", cd):
        errors.append("cd.yml must only be manually triggered by workflow_dispatch")
    # Event triggers are forbidden here. Comment-only lines are ignored.
    if re.search(r"(?m)^\s*(push|pull_request|schedule|workflow_run):", cd):
        errors.append("cd.yml must never deploy on push, PR, schedule or workflow_run")
    for label, token in (
        ("main-only branch guard", 'refs/heads/main'),
        ("manual confirmation", 'DEPLOY_DEV'),
        ("development environment", 'name: development'),
        ("deploy hook secret", 'secrets.RENDER_DEV_DEPLOY_HOOK'),
    ):
        if token not in cd:
            errors.append(f"cd.yml missing {label}")
    if "secrets.RENDER_PROD" in cd or "environment:\n      name: production" in cd:
        errors.append("production deployment is explicitly out of scope")
    return errors

def main() -> int:
    problems = inspect_sources() + verify_cd_policy()
    if problems:
        print("Security guardrails failed (secret values withheld):", file=sys.stderr)
        for line in problems:
            print(" - " + line, file=sys.stderr)
        return 1
    print("OK: high-confidence secret patterns absent; manual DEV-only CD policy present.")
    print("Note: no claim of exhaustive secret scanning or vulnerability assessment.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
