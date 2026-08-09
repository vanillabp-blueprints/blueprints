#!/usr/bin/env python3
"""Check this monorepo against the blueprints index of the '.github' repository.

Three things have to agree: the directories '<blueprint-id>/<platform>/' present here,
the '<modules>' of the root POM, and the entries of blueprints.yaml.

  monorepo directory  ->  has to be a module of the root POM
  monorepo directory  ->  has to be an entry of the index
  index 'available'   ->  the directory has to exist here

The one thing which is deliberately NOT an error by default is a directory whose index
entry still says 'planned': that is the state between adding a blueprint and the CI job
having split it into its own repository, which is what flips the status. Pass --strict
to make it an error - that is how the split job verifies its own result.

Usage: bin/check_index_consistency.py <blueprints.yaml> [--strict]
"""

import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

import yaml

MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PLATFORMS = ("springboot", "quarkus")


def blueprint_directories(root):
    found = set()
    for pom in root.glob("*/*/pom.xml"):
        blueprint_id, platform = pom.parent.parent.name, pom.parent.name
        if platform in PLATFORMS:
            found.add((blueprint_id, platform))
    return found


def pom_modules(root):
    project = ElementTree.parse(root / "pom.xml").getroot()
    modules = set()
    for module in project.findall("m:modules/m:module", MAVEN_NS):
        parts = (module.text or "").strip().split("/")
        if len(parts) == 2:
            modules.add((parts[0], parts[1]))
        else:
            modules.add((module.text, None))
    return modules


def main():
    args = [arg for arg in sys.argv[1:] if arg != "--strict"]
    strict = "--strict" in sys.argv[1:]
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        sys.exit(2)

    root = Path(__file__).resolve().parent.parent
    index = yaml.safe_load(Path(args[0]).read_text(encoding="utf-8"))

    indexed = {
        (blueprint["id"], platform): blueprint["platforms"][platform]["status"]
        for blueprint in index["blueprints"]
        for platform in PLATFORMS
    }
    directories = blueprint_directories(root)
    modules = pom_modules(root)

    errors, warnings = [], []

    for blueprint_id, platform in sorted(directories):
        if (blueprint_id, platform) not in modules:
            errors.append(
                f"{blueprint_id}/{platform}: exists but is not a <module> of the root"
                " POM, so it is never built"
            )
        status = indexed.get((blueprint_id, platform))
        if status is None:
            errors.append(
                f"{blueprint_id}/{platform}: exists but has no entry in blueprints.yaml"
            )
        elif status != "available":
            message = (
                f"{blueprint_id}/{platform}: exists but the index says"
                f" '{status}' instead of 'available'"
            )
            (errors if strict else warnings).append(message)

    for module in sorted(modules):
        if module not in directories:
            errors.append(
                f"{'/'.join(str(part) for part in module)}: is a <module> of the root POM"
                " but does not exist"
            )

    for (blueprint_id, platform), status in sorted(indexed.items()):
        if status == "available" and (blueprint_id, platform) not in directories:
            errors.append(
                f"{blueprint_id}/{platform}: the index says 'available' but the"
                " directory does not exist here"
            )

    for warning in warnings:
        print(f"WARNING {warning}")
    if errors:
        print("\nThe monorepo and the blueprints index disagree:\n", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        print(f"\n{len(errors)} problem(s) found.", file=sys.stderr)
        sys.exit(1)

    print(
        f"monorepo and index agree: {len(directories)} blueprint directories,"
        f" {sum(1 for status in indexed.values() if status == 'available')}"
        " marked available"
    )


if __name__ == "__main__":
    main()
