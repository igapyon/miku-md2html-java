#!/usr/bin/env python3
"""Read the root Maven artifactId and version without resolving build plugins."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

namespace = "{http://maven.apache.org/POM/4.0.0}"
project = ET.parse(Path(__file__).resolve().parent.parent / "pom.xml").getroot()
artifact_id = project.findtext(f"{namespace}artifactId")
version = project.findtext(f"{namespace}version")
if not artifact_id or not version or "${" in artifact_id or "${" in version:
    print("root Maven artifactId/version must be explicit in pom.xml", file=sys.stderr)
    raise SystemExit(1)
print(artifact_id)
print(version)
