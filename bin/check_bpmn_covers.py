#!/usr/bin/env python3
"""Check that the index names every kind of event the BPMN of a blueprint uses.

An agent picks blueprints by matching the BPMN it has to implement against 'covers.bpmn' of
the index. A blueprint whose process has an error boundary event, while its entry does not
say so, is never picked for one, and its README is never read by the agent which needs it.
That happened to bpmn-user-task, bpmn-service-task and bpmn-async-task.

'covers.bpmn' is written by hand and names what a blueprint is good for, so it is not the
list of every element in its process. A plain service task or a gateway is in nearly every
process, and listing it everywhere would make the list useless for picking. Events are
different: each kind needs code of its own, and that is what the agent looks for. So this
script asks only about events. For each blueprint it reads the BPMN files below
'src/main/' of both platforms and requires these entries:

  <bpmn:boundaryEvent>                      bpmn:BoundaryEvent
  <bpmn:boundaryEvent> with an <X> inside   bpmn:BoundaryEvent/bpmn:X
  any <bpmn:xEventDefinition>               bpmn:XEventDefinition

BPMN files of the tests are left out. They are what a test deploys to get somewhere, for
example the old version of a process, and not what the blueprint shows.

Usage: bin/check_bpmn_covers.py <blueprints.yaml>
"""

import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

import yaml

BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
PLATFORMS = ("springboot", "quarkus")


def index_name(tag):
    """'{namespace}timerEventDefinition' -> 'bpmn:TimerEventDefinition'."""

    local = tag[len(BPMN):]
    return "bpmn:" + local[0].upper() + local[1:]


def events_of(bpmn_file):
    """The entries of 'covers.bpmn' the events of one BPMN file ask for."""

    found = set()
    for element in ElementTree.parse(bpmn_file).getroot().iter():
        if not isinstance(element.tag, str) or not element.tag.startswith(BPMN):
            continue
        name = index_name(element.tag)
        if name.endswith("EventDefinition"):
            found.add(name)
        if name == "bpmn:BoundaryEvent":
            found.add(name)
            for child in element:
                if child.tag.startswith(BPMN) and child.tag.endswith("EventDefinition"):
                    found.add(f"{name}/{index_name(child.tag)}")
    return found


def bpmn_files(blueprint_directory):
    for file in sorted(blueprint_directory.rglob("*.bpmn")):
        parts = file.relative_to(blueprint_directory).parts
        if "target" in parts or "node_modules" in parts:
            continue
        if "src" in parts and parts[parts.index("src") + 1:][:1] == ("main",):
            yield file


def main():

    if len(sys.argv) != 2:
        sys.exit("Usage: bin/check_bpmn_covers.py <blueprints.yaml>")
    index = yaml.safe_load(Path(sys.argv[1]).read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent.parent

    problems, compared = [], 0
    for entry in index.get("blueprints", []):
        covered = set(entry.get("covers", {}).get("bpmn") or [])
        used = {}
        for platform in PLATFORMS:
            for directory in root.glob(f"*/{entry['id']}/{platform}"):
                for file in bpmn_files(directory):
                    for name in events_of(file):
                        used.setdefault(name, file.relative_to(root))
        if not used:
            continue
        compared += 1
        for name in sorted(set(used) - covered):
            problems.append(
                f"{entry['id']}: 'covers.bpmn' lacks '{name}', which {used[name]} uses"
            )

    if problems:
        print("The index does not name every event the blueprints use:\n", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print(
            f"\n{len(problems)} problem(s) found. Add each entry to 'covers.bpmn' of the"
            " blueprint in blueprints.yaml of vanillabp-blueprints/.github, so an agent"
            " which matches a BPMN against the index finds the blueprint.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"the index names every event: {compared} blueprint(s) with BPMN compared")


if __name__ == "__main__":
    main()
