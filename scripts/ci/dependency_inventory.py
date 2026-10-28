#!/usr/bin/env python3
"""Document direct dependency declarations; this does NOT scan vulnerabilities."""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POM = ROOT / "backend/pom.xml"
GRADLE = ROOT / "android-app/app/build.gradle.kts"
DEST = ROOT / "reports/dependency-inventory.txt"

pom = ET.parse(POM).getroot()
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
backend = []
for dep in pom.findall("./m:dependencies/m:dependency", ns):
    group = (dep.findtext("m:groupId", default="", namespaces=ns) or "").strip()
    artifact = (dep.findtext("m:artifactId", default="", namespaces=ns) or "").strip()
    version = (dep.findtext("m:version", default="managed", namespaces=ns) or "managed").strip()
    backend.append(f"{group}:{artifact}:{version}")
android = re.findall(
    r'(?:implementation|annotationProcessor|testImplementation|androidTestImplementation)'
    r'\s*\(\s*"([^"]+)"\s*\)',
    GRADLE.read_text(encoding="utf-8"),
)
report = (
    "# Inventory of direct declarations, not transitive or CVE audit\n"
    f"# Maven dependencies ({len(backend)})\n" + "\n".join(sorted(backend)) + "\n\n"
    f"# Android Gradle dependency coordinates ({len(android)})\n" + "\n".join(sorted(android)) + "\n"
)
DEST.parent.mkdir(parents=True, exist_ok=True)
DEST.write_text(report, encoding="utf-8")
print(f"Inventory: {len(backend)} Maven, {len(android)} Android direct dependencies")
