#!/usr/bin/env python3
"""Keep the test harness copies of all blueprints identical to their reference.

Every blueprint carries its own copy of the harness classes instead of depending on a
shared module: a blueprint is split into a repository of its own, has to build there after
a plain clone, and an agent has to be able to read the code which verifies its work. The
price is duplication, and this script is what makes the price affordable.

  templates/test-harness/<platform>/workflow-module/*.java   -> a workflow module's tests
  templates/test-harness/<platform>/application/*.java       -> the application's tests

Both are copied below '<maven-module>/src/test/java/', usually into
'blueprint/workflowmodule/'. A copy may sit in another package - an application whose own
package is not above its workflow modules keeps its tests next to itself - and the package
declaration is rewritten to wherever the copy lives. Which files belong where is decided by
what is already there: the script REFRESHES existing copies, it never decides that a
blueprint needs one. Adding the first copy is part of creating a blueprint.

Usage: bin/sync_harness.py            copy the reference over every existing copy
       bin/sync_harness.py --check    fail if a copy differs from its reference (CI)
"""

import sys
from pathlib import Path

PLATFORMS = ("springboot", "quarkus")
SOURCE_ROOT = Path("src/test/java")


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
    for pom in sorted(root.glob(f"*/*/{platform}/**/pom.xml")):
        sources = pom.parent / SOURCE_ROOT
        if not sources.is_dir():
            continue
        for java in sorted(sources.glob("**/*.java")):
            reference = known.get(java.name)
            if reference is not None:
                found.append((java, reference))
    return found


def package_of(root, copy):
    """The Java package a file belongs to, taken from where it lies."""
    for parent in copy.parents:
        if parent.name == "java" and parent.parent.name == "test":
            return ".".join(copy.parent.relative_to(parent).parts)
    raise ValueError(f"{copy} is not below src/test/java")


def content_for(reference, package):
    """The reference, declaring the package the copy lives in."""
    text = reference.read_text(encoding="utf-8")
    first, rest = text.split("\n", 1)
    if not first.startswith("package "):
        raise ValueError(f"{reference} does not start with a package declaration")
    return f"package {package};\n{rest}"


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
            wanted = content_for(reference, package_of(root, copy))
            if copy.read_text(encoding="utf-8") == wanted:
                continue
            if check:
                differing.append((copy.relative_to(root), reference.relative_to(root)))
            else:
                copy.write_text(wanted, encoding="utf-8")
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
