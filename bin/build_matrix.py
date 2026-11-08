#!/usr/bin/env python3
"""Build the CI matrix: which blueprint is built against which BPMS.

The blueprints are the directories that exist, as everywhere else in this repository.
Which BPMS a blueprint is built against comes from its 'bpms' list in the index, so a
blueprint which cannot run on an engine says so in ONE place, next to everything else it
declares, rather than in a workflow file.

Only engines this CI can actually run are emitted (see RUNNABLE): the Process-Engine-API
adapter is developed mock-first and has no engine behind it, so a blueprint could not
execute a workflow on it.

A directory without an index entry is built against every runnable engine. Being absent
from the index is a finding of its own (bin/check_index_consistency.py), and until it is
fixed the blueprint is tested MORE rather than less.

A second argument narrows the matrix to what a change touches, which is what a pull request
gets: a file inside '<group>/<blueprint-id>/<platform>/' builds that blueprint, a Markdown file
at the top level builds nothing, and anything else - bin/, templates/, the root POM, a
workflow - builds everything, because it can affect every blueprint. The aggregator job builds
all modules on every run regardless, and the nightly run builds every blueprint with its tests,
so nothing is uncovered; what a pull request buys with the narrower matrix is about a hundred
jobs which do not fetch the same artifacts from Maven Central at the same second.

The list comes out in PARTS parts, one JSON array per line, because GitHub runs at most 256
jobs from one matrix. With four engines a full run has more than that, so the workflows have
one matrix job per part. The parts differ in size by one at most, and a part may be empty. A
part which would grow beyond the limit stops this script with a message, and then PARTS and
the workflows get one part more.

A cockpit blueprint with a user interface comes in parts: '<base>-backend', '<base>-frontend'
and the parts which take both, such as '<base>-application' and '<base>-bundle'. Only the
backend builds on its own. The frontend is an npm project without a POM. The other parts need
the workflow module of the backend and the UI jar of a frontend, and neither is published from
this repository. So they are not built here at all, and a change inside one of them or inside a
frontend builds nothing here. Their own workflows build the parts one after the other
(.github/workflows/cockpit-applications.yaml and frontend.yaml). '--chain' prints what such a
workflow builds, and '--chained-modules' what the aggregator of build.yaml leaves out.

Usage: bin/build_matrix.py <blueprints.yaml> [<file with changed paths, one per line>]
       Prints PARTS lines, each one JSON array of {"blueprint": ..., "bpms": ...} objects.
       bin/build_matrix.py --chain <blueprints.yaml>
       Prints one JSON object: "frontends", a list of {"frontend", "artifact"}, and "builds", a
       list of {"part", "frontend", "artifact", "bpms"}: every application and bundle crossed
       with every frontend of its blueprint and with its engines.
       bin/build_matrix.py --chained-modules
       Prints the chained parts which are modules of the root POM, as a value for Maven's
       '-pl', each with '!' in front, so the aggregator leaves them out. Empty if there is none.
"""

import json
import sys
from pathlib import Path
from xml.etree import ElementTree

PLATFORMS = ("springboot", "quarkus")

# The engines the build matrix can run: Camunda 7, Operaton and CIB seven are embedded,
# Camunda 8 is started as a container by the workflow.
RUNNABLE = ("camunda7", "camunda8", "operaton", "cibseven")

# GitHub refuses a matrix of more than 256 jobs, and it does not say so in a way a reader of
# the run notices: the matrix job just never starts. So the list is cut into this many parts,
# and the workflows build.yaml and nightly.yaml have one matrix job per part. Three parts take
# 768 jobs, a full run with four engines has about 260.
PARTS = 3

# What GitHub allows in one matrix.
MATRIX_LIMIT = 256

# The part of a cockpit blueprint which builds on its own, and the one built with npm.
BACKEND = "-backend"
FRONTEND = "-frontend"

# The chained parts which the workflow cockpit-applications.yaml builds. Other chained parts,
# such as an application which takes its user interface from nginx, have workflows of their own.
CHAIN_PARTS = ("-application", "-bundle")

MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def chained(root, group, blueprint_id):
    """Whether a blueprint is a part which needs the backend of its blueprint to be built first.

    :return: the base of the blueprint, such as 'cockpit-user-task', or None
    """

    for backend in (root / group).glob(f"*{BACKEND}"):
        base = backend.name[: -len(BACKEND)]
        if blueprint_id.startswith(f"{base}-") and blueprint_id != backend.name:
            return base
    return None


def blueprint_directories(root, with_chained=False):
    """Every '<group>/<blueprint-id>/<platform>/' with a POM, sorted.

    :param with_chained: whether the parts which cannot build alone are listed as well
    """

    directories = []
    for pom in sorted(root.glob("*/*/*/pom.xml")):
        directory = pom.parent
        if directory.name not in PLATFORMS:
            continue
        relative = directory.relative_to(root)
        if not with_chained and chained(root, relative.parts[0], relative.parts[1]):
            continue
        directories.append(relative)
    return directories


def bpms_of(index, blueprint_id):
    """The runnable engines of one blueprint, or all of them if the index has no entry."""

    for blueprint in index.get("blueprints", []):
        if blueprint.get("id") != blueprint_id:
            continue
        declared = blueprint.get("bpms") or []
        runnable = [bpms for bpms in RUNNABLE if bpms in declared]
        # an entry declaring nothing runnable is a mistake in the index, not a reason to
        # build nothing - the consistency check reports it, the matrix keeps testing
        return runnable or list(RUNNABLE)
    return list(RUNNABLE)


def wanted(directories, changed, root):
    """The directories a change asks for, out of all of them.

    :param directories: every blueprint directory
    :param changed: the paths a change touched
    :param root: the root of this repository
    :return: the directories to build
    """

    touched = set()
    for path in changed:
        directory = blueprint_directory_of(path, directories)
        if directory is not None:
            touched.add(directory)
            continue
        parts = path.split("/")
        if len(parts) > 2 and chained(root, parts[0], parts[1]):
            # a frontend, an application or a bundle of a cockpit blueprint: its own workflow
            # builds it, together with the backend it needs
            continue
        if path.count("/") == 0 and path.endswith(".md"):
            # documentation at the top level, README.md and its kind, builds nothing
            continue
        # bin/, templates/, the root POM, a workflow: anything of this reaches every blueprint
        return list(directories)
    return [directory for directory in directories if directory in touched]


def blueprint_directory_of(path, directories):
    """:return: the blueprint directory a path belongs to, or None"""

    for directory in directories:
        if path.startswith(f"{directory}/"):
            return directory
    return None


def chain(root, index):
    """What the workflow cockpit-applications.yaml builds, see the usage above."""

    frontends = []
    builds = []
    for directory in blueprint_directories(root, with_chained=True):
        group, blueprint_id = directory.parts[0], directory.parts[1]
        base = chained(root, group, blueprint_id)
        if base is None or blueprint_id not in [f"{base}{part}" for part in CHAIN_PARTS]:
            continue
        for package in sorted((root / group / f"{base}{FRONTEND}").glob("*/package.json")):
            frontend = package.parent.relative_to(root)
            artifact = "ui-jar-" + "-".join(frontend.parts[1:])
            entry = {"frontend": str(frontend), "artifact": artifact}
            if entry not in frontends:
                frontends.append(entry)
            for bpms in bpms_of(index, blueprint_id):
                builds.append({"part": str(directory), "bpms": bpms, **entry})
    return {"frontends": frontends, "builds": builds}


def chained_modules(root):
    """The chained parts the aggregator has to leave out, as a value for '-pl'."""

    project = ElementTree.parse(root / "pom.xml").getroot()
    modules = {
        (module.text or "").strip() for module in project.findall("m:modules/m:module", MAVEN_NS)
    }
    excluded = [
        f"!{directory}"
        for directory in blueprint_directories(root, with_chained=True)
        if str(directory) in modules and chained(root, directory.parts[0], directory.parts[1])
    ]
    return ",".join(excluded)


def main():

    root = Path(__file__).resolve().parent.parent

    if sys.argv[1:] == ["--chained-modules"]:
        print(chained_modules(root))
        return 0

    # Only here, because the aggregator job asks for the chained modules without PyYAML.
    import yaml

    if len(sys.argv) == 3 and sys.argv[1] == "--chain":
        index = yaml.safe_load(Path(sys.argv[2]).read_text(encoding="utf-8")) or {}
        print(json.dumps(chain(root, index), separators=(",", ":")))
        return 0
    if len(sys.argv) not in (2, 3) or sys.argv[1].startswith("--"):
        print(__doc__, file=sys.stderr)
        return 2
    index = yaml.safe_load(Path(sys.argv[1]).read_text(encoding="utf-8")) or {}

    directories = [str(directory) for directory in blueprint_directories(root)]
    if len(sys.argv) == 3:
        changed = [
            line.strip()
            for line in Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        directories = wanted(directories, changed, root)

    combinations = []
    for directory in directories:
        blueprint_id = Path(directory).parent.name
        for bpms in bpms_of(index, blueprint_id):
            combinations.append({"blueprint": directory, "bpms": bpms})

    for part in parts_of(combinations):
        print(json.dumps(part, separators=(",", ":")))
    return 0


def parts_of(combinations):
    """:return: PARTS lists in a row, which differ in size by one at most"""

    size, rest = divmod(len(combinations), PARTS)
    if size + (1 if rest else 0) > MATRIX_LIMIT:
        raise SystemExit(
            f"{len(combinations)} jobs do not fit into {PARTS} matrices of at most"
            f" {MATRIX_LIMIT} jobs. Raise PARTS in bin/build_matrix.py and add a matrix job"
            " for the new part to build.yaml and nightly.yaml."
        )
    parts = []
    start = 0
    for number in range(PARTS):
        end = start + size + (1 if number < rest else 0)
        parts.append(combinations[start:end])
        start = end
    return parts


if __name__ == "__main__":
    sys.exit(main())
