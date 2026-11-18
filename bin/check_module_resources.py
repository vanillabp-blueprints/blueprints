#!/usr/bin/env python3
"""Check that the resources and the classes of a workflow module live in a namespace of their own.

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

Classes follow the same rule with a Java package (CONTRIBUTING.md, Rule 2). Two more checks:

  - Every class in 'src/main/java' of a workflow module lies in the package of one of its
    IDs, or below it: 'loan-approval' is the package 'loanapproval'. A second workflow of the
    module goes below it, as 'loanapproval.ratewatch', never next to it.
  - A Spring Boot application class ('@SpringBootApplication') is not in a package above the
    package of a workflow module of its blueprint. Otherwise its component scan reaches into
    the module, and the auto-configuration the module brings is never tested. A module which is
    its own application, like 'module-standalone', is not compared with itself.

Checked: every 'src/main/resources' below the 'blueprints-*' folders, outside of 'target' and
'node_modules', and the 'src/main/java' next to it.

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
    problems += package_problems(root, all_resources)
    for problem in problems:
        print(problem)
    if problems:
        return 1
    print(f"the resources and classes of all {modules} modules live in the namespace of their "
          "workflow module")
    return 0


def package_of(java: Path, sources: Path) -> list[str]:
    return list(java.parent.relative_to(sources).parts)


def is_application(resources: Path) -> bool:
    return any((resources / name).is_file() for name in APPLICATION_FILES)


def package_problems(root: Path, all_resources: list[Path]) -> list[str]:
    """Classes outside the package of their module, and applications above a module."""

    problems = []
    module_packages: dict[tuple[str, ...], set[tuple[str, ...]]] = {}
    for resources in all_resources:
        ids = declared_ids(resources)
        sources = resources.parent / "java"
        if not ids or is_application(resources) or not sources.is_dir():
            continue
        names = {workflow_module_id.replace("-", "") for workflow_module_id in ids}
        blueprint = resources.relative_to(root).parts[:3]
        for java in sorted(sources.rglob("*.java")):
            parts = package_of(java, sources)
            below = [index for index, part in enumerate(parts) if part in names]
            if not below:
                problems.append(
                    f"{java.relative_to(root)}: a class of the workflow module "
                    f"'{'/'.join(sorted(ids))}' lies outside its package. Move it below "
                    f"'{'/'.join(sorted(names))}'. A package next to the module clashes with a "
                    "workflow module of that name."
                )
                continue
            module_packages.setdefault(blueprint, set()).add(tuple(parts[:below[0] + 1]))
    for resources in all_resources:
        sources = resources.parent / "java"
        if not is_application(resources) or declared_ids(resources) or not sources.is_dir():
            continue
        blueprint = resources.relative_to(root).parts[:3]
        for java in sorted(sources.rglob("*.java")):
            if "@SpringBootApplication" not in java.read_text(encoding="utf-8"):
                continue
            parts = tuple(package_of(java, sources))
            for module in sorted(module_packages.get(blueprint, ())):
                if module[:len(parts)] == parts:
                    problems.append(
                        f"{java.relative_to(root)}: the package of the application is above the "
                        f"workflow module in '{'.'.join(module)}', so its component scan reaches "
                        "into the module. Put the application into a package of its own, such as "
                        "'blueprint.application', and let the module wire itself with an "
                        "auto-configuration."
                    )
    return problems


if __name__ == "__main__":
    sys.exit(main())
