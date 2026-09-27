#!/usr/bin/env python3
"""Check that a file table of a blueprint names files which are there.

Every blueprint describes itself in tables whose first column is 'File': the core files
and the boilerplate files in AGENTS.md, the delta to the base blueprint and 'How it
works' in README.md. When a file moves, those tables stay behind and no build notices.
That is how 22 blueprints came to name a profile file which had moved a month before,
and story 683 corrected them by hand.

Checked for '<group>/<blueprint-id>/<platform>/AGENTS.md' and its README.md, in every
table whose first header cell is 'File':

  - every path in the first cell of a row leads to a file or directory of the blueprint,
  - a row saying the file is 'deleted' or 'gone' names one which is really not there.

Paths are written in three forms and all three are resolved:

  loan-approval/pom.xml                            from the root of the blueprint
  loan-approval/src/test/.../WorkflowModuleTest.java   '...' is what the writer left out
  .../processes/<adapter-id>/loan_approval.bpmn    '<...>' is a name the reader chooses

A README names a file the way its section reads it, 'Workflow.java' rather than the path
from the root, so a path which does not resolve from the root is looked for anywhere
below it.

A first cell which is not meant as a file needs no exception of its own: the header of
the table decides. A table of beans, of BPMN element ids or of placeholders is headed
'Bean', 'Name' or 'Placeholder' and is not read here. What a build writes is not in the
repository, so a path below 'target/' is skipped as well.

Usage: bin/check_table_paths.py
"""

import re
import sys
from pathlib import Path

PLATFORMS = ("springboot", "quarkus")

DOCUMENTS = ("AGENTS.md", "README.md")

# The tables this script reads, by the name of their first column.
FILE_COLUMN = "File"

CODE_SPAN = re.compile(r"`([^`]+)`")

# What a build writes. It is not committed, so a table naming it is right even though
# nothing is there.
BUILD_OUTPUT = "target"

# A file name without a directory in front of it is only a file name if it ends in one of
# these. Without the list every 'com.acme' and every 'blueprint.workflowmodule' would be
# read as a path.
EXTENSIONS = {
    ".bpmn", ".dmn", ".idx", ".imports", ".java", ".json", ".md", ".png",
    ".properties", ".py", ".sh", ".sql", ".txt", ".xml", ".yaml", ".yml",
}

# How a row says that the file it names is the one this blueprint does without.
GONE = re.compile(r"^(deleted|gone)\b", re.IGNORECASE)


def is_separator(cells):
    """The '|---|---|' line below the header."""

    return bool(cells) and bool(cells[0]) and set(cells[0]) <= set("-: ")


def file_table_rows(text):
    """Every row of every table headed 'File', as (line number, cells)."""

    rows = []
    header = None
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if not stripped.startswith("|"):
            header = None
            continue
        cells = [cell.strip() for cell in stripped.strip("|").split("|")]
        if is_separator(cells):
            continue
        if header is None:
            header = cells[0]
            continue
        if header == FILE_COLUMN:
            rows.append((number, cells))
    return rows


def paths_in(cell):
    """The paths a cell names, which is what is written in backticks and looks like one."""

    found = []
    for token in CODE_SPAN.findall(cell):
        if token.startswith(("http://", "https://")):
            continue
        # A cell naming two files sometimes shortens the second one to the part which
        # differs, as in '`loan-approval-test.yaml`, `-prod.yaml`'. The first one is
        # checked and carries the directory, so the continuation is not a path.
        if token.startswith("-"):
            continue
        if "/" not in token and Path(token).suffix not in EXTENSIONS:
            continue
        if BUILD_OUTPUT in Path(token).parts:
            continue
        found.append(token)
    return found


def as_pattern(path):
    """The path as a glob: the two shorthands become wildcards, the rest stays."""

    pattern = re.sub(r"<[^>]*>", "*", path.rstrip("/"))
    if pattern.startswith(".../"):
        pattern = "**/" + pattern[4:]
    if pattern.endswith("/..."):
        pattern = pattern[:-3] + "**"
    pattern = pattern.replace("/.../", "/**/")
    return pattern.replace("...", "*")


def resolves(blueprint, path):
    """Whether the blueprint has what the path names, from its root or anywhere below."""

    pattern = as_pattern(path)
    for candidate in (pattern, "**/" + pattern):
        if not any(character in candidate for character in "*?["):
            if (blueprint / candidate).exists():
                return True
            continue
        for match in blueprint.glob(candidate):
            if BUILD_OUTPUT not in match.relative_to(blueprint).parts:
                return True
    return False


def check_document(document, blueprint, errors, root):

    if not document.exists():
        # its absence is what bin/check_docs_structure.py is there for
        return 0

    display = document.relative_to(root)
    checked = 0
    for number, cells in file_table_rows(document.read_text(encoding="utf-8")):
        says_gone = len(cells) > 1 and GONE.match(cells[1])
        for path in paths_in(cells[0]):
            checked += 1
            found = resolves(blueprint, path)
            if says_gone and found:
                errors.append(
                    f"{display}:{number}: says '{path}' is gone, but the blueprint"
                    " still has it"
                )
            elif not found and not says_gone:
                errors.append(
                    f"{display}:{number}: names '{path}', which this blueprint does"
                    " not have"
                )
    return checked


def main():

    root = Path(__file__).resolve().parent.parent
    errors = []
    documents = checked = 0

    for pom in sorted(root.glob("*/*/*/pom.xml")):
        blueprint = pom.parent
        if blueprint.name not in PLATFORMS:
            continue
        for name in DOCUMENTS:
            documents += 1
            checked += check_document(blueprint / name, blueprint, errors, root)

    if errors:
        print("File tables name files which are not there:\n", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        print(
            f"\n{len(errors)} problem(s) found. A row of a table headed"
            f" '{FILE_COLUMN}' names a file of the blueprint, so either the row or the"
            " file has to follow the other one.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"file tables are honest: {checked} path(s) in {documents} document(s)")


if __name__ == "__main__":
    main()
