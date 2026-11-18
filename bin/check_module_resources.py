#!/usr/bin/env python3
"""Check that the resources of a workflow module live in a directory of its own.

Spring Boot and Quarkus do not deploy a workflow module on its own. All modules of an
application share one classpath, so two modules with a file at the same path clash, and one
of the files is never read. Classes avoid this with a Java package per module, resources with
a directory named after the workflow module ID (CONTRIBUTING.md, "The same applies to
resources").

So 'src/main/resources' of a workflow module holds only two entries:

  META-INF/                 the marker 'workflow-module' and the files of the platform
  <workflow-module-id>/     everything else: configuration, BPMN, OpenAPI files, templates

A module is a workflow module when its 'META-INF/workflow-module' names the IDs. A library
which belongs to a workflow module, such as a test driver or a commons module, has no marker.
It may use the directory of any workflow module ID of its blueprint.

An application module is not checked. It holds the modules together and has no neighbours on
the classpath, so 'application.yaml' and the other files of the application stay at the top.
A module counts as an application when it has an 'application.yaml', 'application.yml' or
'application.properties' at the top of its resources.

Checked: every 'src/main/resources' below the 'blueprints-*' folders, outside of 'target' and
'node_modules'.

Usage: bin/check_module_resources.py
"""

import sys
from pathlib import Path

MARKER = Path("META-INF/workflow-module")
APPLICATION_FILES = ("application.yaml", "application.yml", "application.properties")
SKIPPED = {"target", "node_modules"}


def declared_ids(resources: Path) -> list[str]:
    marker = resources / MARKER
    if not marker.is_file():
        return []
    return [line.strip() for line in marker.read_text(encoding="utf-8").splitlines() if line.strip()]


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    all_resources = sorted(
        resources
        for folder in root.glob("blueprints-*")
        for resources in folder.rglob("src/main/resources")
        if resources.is_dir() and not SKIPPED & set(resources.relative_to(root).parts)
    )

    # A library has no marker of its own, so it may use the ID of any workflow module of its
    # blueprint. A blueprint is '<group>/<id>/<platform>'.
    ids_by_blueprint: dict[tuple[str, ...], set[str]] = {}
    for resources in all_resources:
        blueprint = resources.relative_to(root).parts[:3]
        ids_by_blueprint.setdefault(blueprint, set()).update(declared_ids(resources))

    problems = []
    modules = 0
    for resources in all_resources:
        if any((resources / name).is_file() for name in APPLICATION_FILES):
            continue
        modules += 1
        own_ids = declared_ids(resources)
        allowed = set(own_ids or ids_by_blueprint[resources.relative_to(root).parts[:3]])
        target = "/".join(sorted(allowed)) if allowed else "<workflow-module-id>"
        for entry in sorted(resources.iterdir()):
            if entry.name == "META-INF" and entry.is_dir():
                continue
            if entry.name in allowed and entry.is_dir():
                continue
            problems.append(
                f"{entry.relative_to(root)}: only 'META-INF/' and a directory named after the "
                f"workflow module ID belong at the top of the resources of a module. Move it "
                f"into '{target}/'. Workflow modules share one classpath, so the same path in "
                "two modules is a clash."
            )
    for problem in problems:
        print(problem)
    if problems:
        return 1
    print(f"the resources of all {modules} modules live in the directory of their workflow module")
    return 0


if __name__ == "__main__":
    sys.exit(main())
