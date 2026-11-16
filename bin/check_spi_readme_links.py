#!/usr/bin/env python3
"""Check that no file links an anchor of the README of spi-for-java.

The user documentation of spi-for-java moved from its README into its wiki, one page per
topic. The README kept named anchors for a while, so the old links still worked, but they
only lead to a list of wiki pages. A link to 'spi-for-java#wire-up-a-task' therefore points
at the wiki page itself, here https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-task.
A link to the repository without an anchor is fine.

Checked in every file git knows, minus binary files:

  - https://github.com/vanillabp/spi-for-java#some-anchor (with or without a slash),
  - https://github.com/vanillabp/spi-for-java/blob/<branch>/README.md#some-anchor.

Usage: bin/check_spi_readme_links.py
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

README_ANCHOR_LINK = re.compile(
    r"github\.com/vanillabp/spi-for-java(?:/?|/blob/[^/\s)\"']+/README\.md)#[\w-]+",
    re.IGNORECASE,
)

WIKI_HINT = (
    "Link the page of the spi-for-java wiki which took over the section instead. The list of"
    " pages is https://github.com/vanillabp/spi-for-java/wiki, for example"
    " https://github.com/vanillabp/spi-for-java/wiki/The-workflow-aggregate#natural-ids."
)


def tracked_files():
    output = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True
    ).stdout
    return [name for name in output.decode().split("\0") if name]


def findings_in(name):
    path = ROOT / name
    if not path.is_file():
        return []
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return []
    found = []
    for number, line in enumerate(text.splitlines(), start=1):
        for match in README_ANCHOR_LINK.finditer(line):
            found.append(f"{name}:{number}: links a README anchor of spi-for-java: {match.group(0)}")
    return found


def main():
    this_script = Path(__file__).resolve().relative_to(ROOT).as_posix()
    findings = []
    files = [name for name in tracked_files() if name != this_script]
    for name in files:
        findings.extend(findings_in(name))
    if findings:
        print("\n".join(findings))
        print(f"\n{len(findings)} link(s) to a README anchor of spi-for-java. {WIKI_HINT}")
        return 1
    print(f"no link to a README anchor of spi-for-java: {len(files)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
