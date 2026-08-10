#!/usr/bin/env python3
"""Keep the test harness copies of all blueprints identical to their reference.

Every blueprint carries its own copy of the harness classes instead of depending on a
shared module: a blueprint is split into a repository of its own, has to build there after
a plain clone, and an agent has to be able to read the code which verifies its work. The
price is duplication, and this script is what makes the price affordable.

  templates/test-harness/<platform>/workflow-module/*.java   -> a workflow module's tests
  templates/test-harness/<platform>/application/*.java       -> the application's tests

Both are copied into '<maven-module>/src/test/java/blueprint/workflowmodule/'. Which files
belong where is decided by what is already there: the script REFRESHES existing copies, it
never decides that a blueprint needs one. Adding the first copy is part of creating a
blueprint.

Usage: bin/sync_harness.py            copy the reference over every existing copy
       bin/sync_harness.py --check    fail if a copy differs from its reference (CI)
"""

import sys
from pathlib import Path

PLATFORMS = ("springboot", "quarkus")
TARGET_PACKAGE = Path("src/test/java/blueprint/workflowmodule")


def references(root, platform):
    """The harness files of a platform, by file name."""
    directory = root / "templates" / "test-harness" / platform
    if not directory.is_dir():
        return {}
    return {
        java.name: java
        for java in sorted(directory.glob("*/*.java"))
    }


def copies(root, platform, known):
    """Every existing copy of a harness file, as (copy, reference) pairs."""
    found = []
    for pom in sorted(root.glob(f"*/{platform}/**/pom.xml")):
        target = pom.parent / TARGET_PACKAGE
        if not target.is_dir():
            continue
        for java in sorted(target.glob("*.java")):
            reference = known.get(java.name)
            if reference is not None:
                found.append((java, reference))
    return found


def main():
    check = "--check" in sys.argv[1:]
    root = Path(__file__).resolve().parent.parent

    differing, total = [], 0

    for platform in PLATFORMS:
        known = references(root, platform)
        if not known:
            continue
        for copy, reference in copies(root, platform, known):
            total += 1
            if copy.read_bytes() == reference.read_bytes():
                continue
            if check:
                differing.append((copy.relative_to(root), reference.relative_to(root)))
            else:
                copy.write_bytes(reference.read_bytes())
                print(f"updated {copy.relative_to(root)}")

    if differing:
        print(
            "Test harness copies differ from their reference:\n",
            file=sys.stderr,
        )
        for copy, reference in differing:
            print(f"  - {copy}\n    differs from {reference}", file=sys.stderr)
        print(
            "\nThe harness is maintained in templates/test-harness/ and copied into every"
            "\nblueprint. Change the reference and run bin/sync_harness.py - do not edit a"
            f"\ncopy. {len(differing)} of {total} copies are out of date.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"test harness: {total} copies in sync")


if __name__ == "__main__":
    main()
