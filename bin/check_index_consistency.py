#!/usr/bin/env python3
"""Check this monorepo against the blueprints index of the '.github' repository.

Three things have to agree: the directories '<group>/<blueprint-id>/<platform>/' present
here, the '<modules>' of the root POM, and the entries of blueprints.yaml.

  monorepo directory     ->  has to be a module of the root POM
  monorepo directory     ->  has to be an entry of the index
  monorepo directory     ->  has to sit in the group directory of its category
  index 'available'      ->  the directory has to exist here
  index 'not-applicable' ->  the directory must NOT exist here

A frontend of the Business Cockpit builds with npm. Its directory is
'<group>/<blueprint-id>/<framework>/' with a package.json, the framework is its platform in
the index, and it is not a module of the root POM. A pom.xml in it is an error: the frontend
shows a build without Maven, and the Maven reactor would pick it up.

The one thing which is deliberately NOT an error by default is a directory whose index
entry still says 'planned': that is the state between adding a blueprint and the CI job
having split it into its own repository, which is what flips the status. Pass --strict
to make it an error - that is how the split job verifies its own result.

'not-applicable' is different: it says the platform does not know what the blueprint is
about, so a directory for it contradicts the index outright. A missing directory is fine
there, --strict included, because nothing is ever going to fill it.

Usage: bin/check_index_consistency.py <blueprints.yaml> [--strict]
"""

import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

import yaml

MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PLATFORMS = ("springboot", "quarkus")

# The UI frameworks a frontend of the Business Cockpit exists for. The index names them as the
# platforms of a frontend, so they are platforms here as well.
FRONTEND_PLATFORMS = ("react", "angular", "vue")

# The group directory a blueprint of a category lives in. It keeps the top level of this
# repository at a handful of entries, and it is a rule rather than a habit because this
# check enforces it.
GROUP_OF_CATEGORY = {
    "module": "blueprints-modules",
    "persistence": "blueprints-persistence",
    "bpmn": "blueprints-bpmn",
    "cockpit": "blueprints-cockpit",
    "showcase": "showcases",
}


def blueprint_directories(root, marker="pom.xml", platforms=PLATFORMS):
    """Every blueprint directory, as (blueprint id, platform) -> group directory."""
    found = {}
    for file in root.glob(f"*/*/*/{marker}"):
        blueprint_id, platform = file.parent.parent.name, file.parent.name
        group = file.parent.parent.parent.name
        if platform in platforms:
            found[(blueprint_id, platform)] = group
    return found


def pom_modules(root):
    project = ElementTree.parse(root / "pom.xml").getroot()
    modules = set()
    for module in project.findall("m:modules/m:module", MAVEN_NS):
        parts = (module.text or "").strip().split("/")
        if len(parts) == 3:
            modules.add((parts[1], parts[2]))
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
        (blueprint["id"], platform): entry["status"]
        for blueprint in index["blueprints"]
        for platform in PLATFORMS + FRONTEND_PLATFORMS
        if (entry := blueprint["platforms"].get(platform)) is not None
    }
    category_of = {blueprint["id"]: blueprint["category"] for blueprint in index["blueprints"]}
    maven_directories = blueprint_directories(root)
    frontends = blueprint_directories(root, "package.json", FRONTEND_PLATFORMS)
    directories = {**maven_directories, **frontends}
    modules = pom_modules(root)

    errors, warnings = [], []

    for blueprint_id, platform in sorted(
        blueprint_directories(root, "pom.xml", FRONTEND_PLATFORMS)
    ):
        errors.append(
            f"{blueprint_id}/{platform}: has a pom.xml. A frontend builds with npm alone,"
            " and the Maven reactor must not find it"
        )

    for blueprint_id, platform in sorted(directories):
        # A frontend is built by npm, so it is no <module> of the root POM.
        is_maven = (blueprint_id, platform) in maven_directories
        if is_maven and (blueprint_id, platform) not in modules:
            errors.append(
                f"{blueprint_id}/{platform}: exists but is not a <module> of the root"
                " POM, so it is never built"
            )
        category = category_of.get(blueprint_id)
        expected_group = GROUP_OF_CATEGORY.get(category)
        if expected_group is not None and directories[(blueprint_id, platform)] != expected_group:
            errors.append(
                f"{directories[(blueprint_id, platform)]}/{blueprint_id}/{platform}: a"
                f" blueprint of category '{category}' belongs into '{expected_group}/'"
            )
        status = indexed.get((blueprint_id, platform))
        if status is None:
            errors.append(
                f"{blueprint_id}/{platform}: exists but has no entry in blueprints.yaml"
            )
        elif status == "not-applicable":
            errors.append(
                f"{blueprint_id}/{platform}: exists although the index says"
                " 'not-applicable' for this platform. Either the reason given there is"
                " wrong, or this directory is"
            )
        elif status != "available":
            message = (
                f"{blueprint_id}/{platform}: exists but the index says"
                f" '{status}' instead of 'available'"
            )
            (errors if strict else warnings).append(message)

    for module in sorted(modules):
        if module not in maven_directories:
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
