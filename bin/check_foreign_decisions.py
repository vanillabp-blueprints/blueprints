#!/usr/bin/env python3
"""Check that no file cites a decision of another repository by its number.

A blueprint used to say "decision 88 of the Camunda 8 adapter" and link into the
DECISIONS.md of that adapter. A number in another repository can change, and a blueprint
somebody copied keeps pointing at it. So a text or a comment here names the behaviour and
links the wiki page of the adapter or the platform which describes it. Only the decisions
of this repository may be cited by number.

Checked in every file git knows, minus binary files:

  - a sentence such as 'decision 12 of the adapter' or 'decision 12 in the Camunda 7
    adapter',
  - a link into the DECISIONS.md of another GitHub repository.

Usage: bin/check_foreign_decisions.py
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# This repository. A link into its own decisions is allowed.
OWN_REPOSITORY = "vanillabp-blueprints/blueprints"

DECISION_BY_NUMBER = re.compile(r"\bdecision\s+#?\d+\s+(?:of|in)\s+the\b", re.IGNORECASE)
DECISIONS_LINK = re.compile(
    r"github\.com/([\w.-]+/[\w.-]+)/(?:blob|tree|raw)/[^\s)]*DECISIONS\.md", re.IGNORECASE
)

WIKI_HINT = (
    "Name the behaviour and link the wiki page of the adapter or the platform which describes"
    " it, for example https://github.com/vanillabp/camunda8-adapter/wiki/Configuration#"
    "a-job-somebody-else-holds. If no wiki page describes it, say what happens in plain words."
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
        if DECISION_BY_NUMBER.search(line):
            found.append(f"{name}:{number}: cites a decision by its number: {line.strip()}")
        for match in DECISIONS_LINK.finditer(line):
            if match.group(1).lower() != OWN_REPOSITORY:
                found.append(
                    f"{name}:{number}: links the decisions of {match.group(1)}: {line.strip()}"
                )
    return found


def main():
    this_script = Path(__file__).resolve().relative_to(ROOT).as_posix()
    findings = []
    files = [name for name in tracked_files() if name != this_script]
    for name in files:
        findings.extend(findings_in(name))
    if findings:
        print("\n".join(findings))
        print(f"\n{len(findings)} citation(s) of a decision of another repository. {WIKI_HINT}")
        return 1
    print(f"no decision of another repository is cited: {len(files)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
