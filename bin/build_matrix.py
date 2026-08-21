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

Usage: bin/build_matrix.py <blueprints.yaml> [<file with changed paths, one per line>]
       Prints one JSON array of {"blueprint": ..., "bpms": ...} objects.
"""

import json
import sys
from pathlib import Path

import yaml

PLATFORMS = ("springboot", "quarkus")

# The engines the build matrix can run: Camunda 7 is embedded, Camunda 8 is started as a
# container by the workflow.
RUNNABLE = ("camunda7", "camunda8")


def blueprint_directories(root):
    """Every '<group>/<blueprint-id>/<platform>/' with a POM, sorted."""

    directories = []
    for pom in sorted(root.glob("*/*/*/pom.xml")):
        directory = pom.parent
        if directory.name not in PLATFORMS:
            continue
        directories.append(directory.relative_to(root))
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


def wanted(directories, changed):
    """The directories a change asks for, out of all of them.

    :param directories: every blueprint directory
    :param changed: the paths a change touched
    :return: the directories to build
    """

    touched = set()
    for path in changed:
        directory = blueprint_directory_of(path, directories)
        if directory is not None:
            touched.add(directory)
            continue
        if path.count("/") == 0 and path.endswith(".md"):
            # documentation at the top level: GAPS.md and its kind build nothing
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


def main():

    if len(sys.argv) not in (2, 3):
        print(__doc__, file=sys.stderr)
        return 2

    root = Path(__file__).resolve().parent.parent
    index = yaml.safe_load(Path(sys.argv[1]).read_text(encoding="utf-8")) or {}

    directories = [str(directory) for directory in blueprint_directories(root)]
    if len(sys.argv) == 3:
        changed = [
            line.strip()
            for line in Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        directories = wanted(directories, changed)

    combinations = []
    for directory in directories:
        blueprint_id = Path(directory).parent.name
        for bpms in bpms_of(index, blueprint_id):
            combinations.append({"blueprint": directory, "bpms": bpms})

    print(json.dumps(combinations, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
