#!/usr/bin/env python3
"""Check that every copy of the rule which keeps adapters out of a workflow module is the same.

The rule is a Maven Enforcer execution with the id 'no-adapter-in-a-workflow-module'. Each
blueprint carries its own copy, because a blueprint must build on its own (CONTRIBUTING.md,
Rule 1). A copy which is not updated keeps working and fails nothing, so an old copy is not
noticed: module-under-load, module-simulator and cockpit-header-extension-backend kept a copy
without 'vanillabp-quarkus-integration' for weeks.

The reference is the copy in the root POM of module-single, the base blueprint. Every other
execution with that id is either

- the same, apart from whitespace, comments included, or
- the switch-off of an application module: the id and '<phase>none</phase>', nothing else.

Checked: every 'pom.xml' below the 'blueprints-*' folders, outside of 'target'.

Usage: bin/check_enforcer_rule.py
"""

import re
import sys
from pathlib import Path

RULE_ID = "no-adapter-in-a-workflow-module"
REFERENCE = Path("blueprints-modules/module-single/springboot/pom.xml")
EXECUTION = re.compile(
    r"<execution>\s*<id>" + re.escape(RULE_ID) + r"</id>.*?</execution>", re.DOTALL
)
SWITCH_OFF = f"<execution> <id>{RULE_ID}</id> <phase>none</phase> </execution>"


def normalized(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r">\s+<", "> <", text)).strip()


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    reference_match = EXECUTION.search((root / REFERENCE).read_text(encoding="utf-8"))
    if reference_match is None:
        print(f"{REFERENCE}: has no execution '{RULE_ID}', so there is nothing to compare with.")
        return 1
    reference = normalized(reference_match.group(0))

    poms = sorted(
        pom
        for folder in root.glob("blueprints-*")
        for pom in folder.rglob("pom.xml")
        if "target" not in pom.relative_to(root).parts
    )
    problems = []
    copies = 0
    for pom in poms:
        for match in EXECUTION.finditer(pom.read_text(encoding="utf-8")):
            copies += 1
            execution = normalized(match.group(0))
            if execution not in (reference, SWITCH_OFF):
                problems.append(
                    f"{pom.relative_to(root)}: the execution '{RULE_ID}' differs from the one "
                    f"in {REFERENCE}. Copy it from there, or switch it off with only "
                    "'<phase>none</phase>' in an application module."
                )
    for problem in problems:
        print(problem)
    if problems:
        return 1
    print(f"the rule '{RULE_ID}' is the same in all {copies} copies of {len(poms)} POMs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
