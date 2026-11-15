#!/usr/bin/env python3
"""Check that no other profile switches off the default BPMS profile of a POM.

A blueprint builds without '-P<bpms>' because one BPMS profile, mostly 'camunda7', is
marked '<activeByDefault>true</activeByDefault>'. Maven switches such a profile off as soon
as any other profile of the same POM is active. A profile which switches itself on, through
a property, a file, the JDK or the operating system, does that on every build where its
condition holds, and the user does not see it happen. The default BPMS profile is then
gone, the module has no BPMS adapter, and the test fails with "No VanillaBP BPMS adapter
found". CI names a BPMS on every build, so CI does not notice; a person who builds the way
the README shows does.

That is what module-simulator and module-under-load did until the 15th of November 2026:
the profile which starts their simulator was on unless 'simulator.base-url' was given.

So a POM with a profile which is active by default has no other profile with an
'<activation>' of its own. What such a profile did goes into the plain build, see
CONTRIBUTING.md. A profile which is only ever named with '-P' is fine, because the user
then knows that the default is gone and names the BPMS too.

Checked: every 'pom.xml' below the 'blueprints-*' folders, outside of 'target'.

Usage: bin/check_default_profiles.py
"""

import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

POM = "{http://maven.apache.org/POM/4.0.0}"


def active_by_default(activation) -> bool:
    flag = activation.find(f"{POM}activeByDefault")
    return flag is not None and (flag.text or "").strip() == "true"


def switches_itself_on(activation) -> bool:
    """True when the activation has a condition other than 'activeByDefault'."""
    return any(child.tag != f"{POM}activeByDefault" for child in activation)


def problems_of(pom: Path) -> list[str]:
    profiles = ElementTree.parse(pom).getroot().findall(f"{POM}profiles/{POM}profile")
    defaults = []
    self_activated = []
    for profile in profiles:
        activation = profile.find(f"{POM}activation")
        if activation is None:
            continue
        profile_id = (profile.findtext(f"{POM}id") or "?").strip()
        if active_by_default(activation):
            defaults.append(profile_id)
        if switches_itself_on(activation):
            self_activated.append(profile_id)
    if not defaults or not self_activated:
        return []
    return [
        f"{pom}: profile '{other}' switches itself on, and then Maven switches off the "
        f"default profile '{', '.join(defaults)}'. Move what '{other}' does into the plain "
        "build (see CONTRIBUTING.md)."
        for other in self_activated
    ]


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    poms = sorted(
        pom
        for folder in root.glob("blueprints-*")
        for pom in folder.rglob("pom.xml")
        if "target" not in pom.relative_to(root).parts
    )
    problems = [problem for pom in poms for problem in problems_of(pom)]
    for problem in problems:
        print(problem)
    if problems:
        return 1
    print(f"{len(poms)} POMs checked, no profile switches off a default profile.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
