#!/usr/bin/env python3
"""Check that the README of a blueprint names every file which differs from its base.

A blueprint with a 'base' in the index is the base blueprint plus a delta. Its README says
which files that delta touches, in the table under '## Delta to the base blueprint'. An
agent which copies only what that table names misses every file the table leaves out, and
the repositories of the two blueprints are complete applications, so nobody sees the gap
without diffing them by hand.

This script does the diff. For every blueprint with a 'base', and for every platform both
of them have, it compares the files of '<group>/<id>/<platform>/' with the files of the
base on the same platform:

  a file only the blueprint has      ->  the table has to name it
  a file only the base has           ->  the table has to name it, usually as 'deleted'
  a file both have, but different    ->  the table has to name it

What counts as different is the code, not the wording around it:

  - Every blueprint has its own name. The id of the base becomes the id of the blueprint
    before the comparison, so 'module-single' in a POM does not count as a change.
  - Comments are removed, and so is the 'name' and 'description' of a POM, and all
    whitespace. A comment which says the same thing in other words, or which is wrapped at
    another column, is not something a reader has to take over.
  - Each blueprint names its own H2 database, so that two of them never share one. That
    name counts as a placeholder, like the id.

A row names a file the way 'bin/check_table_paths.py' reads it: what is written in
backticks in the first cell. 'Service.java' names every file of that name, 'model/' every
file below such a directory, '.../loanapproval/*.java' every file the pattern matches, and
'LoanApprovalIT' the file of that name with any extension. A README which groups files in
one row keeps doing so. The one exception is a file below 'openapi/': the OpenAPI file of a
module and the configuration of the module are both called 'loan-approval.yaml', and they
are different things. A row names the first one as 'openapi/loan-approval.yaml'.

Some files differ in every blueprint and are not part of a delta (ALLOWED below): the
documents which describe the blueprint, and the pictures, which are rendered from its BPMN
or are logos. The files are taken from git, so what a build writes is never compared.

Usage: bin/check_delta_tables.py <blueprints.yaml>
"""

import fnmatch
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

import yaml

# The import below must not leave a __pycache__ directory in bin/ behind.
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_table_paths import CODE_SPAN, as_pattern, file_table_rows  # noqa: E402

PLATFORMS = ("springboot", "quarkus", "react", "angular", "vue")

DELTA_HEADING = "## Delta to the base blueprint"

# Files which differ from the base in every blueprint, and why that is no delta.
ALLOWED = (
    # They describe the blueprint itself. Its README holds the very table checked here.
    "README.md",
    "AGENTS.md",
    # The picture of the BPMN is rendered from the BPMN file, which the table names anyway.
    "docs/*.png",
    # The logos at the top of the README.
    "readme/*",
)

COMMENTS_BY_SUFFIX = {
    ".java": "c",
    ".ts": "c",
    ".tsx": "c",
    ".js": "c",
    ".mjs": "c",
    ".vue": "c-and-xml",
    ".xml": "xml",
    ".bpmn": "xml",
    ".dmn": "xml",
    ".html": "xml",
    ".ftl": "xml",
    ".yaml": "hash",
    ".yml": "hash",
    ".properties": "hash",
    ".sh": "hash",
    ".sql": "sql",
}

# A comment in the C family, written so that a '//' inside a string survives: the strings
# are matched first and kept, the comments are matched second and dropped.
C_COMMENT = re.compile(
    r'("""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'|`(?:\\.|[^`\\])*`)'
    r"|//[^\n]*|/\*[\s\S]*?\*/"
)
XML_COMMENT = re.compile(r"<!--[\s\S]*?-->")
# A '#' starts a comment at the start of a line or after a blank, which leaves the '#' of
# a URL or a colour alone.
HASH_COMMENT = re.compile(r"(^|\s)#[^\n]*")
SQL_COMMENT = re.compile(r"(^|\s)--[^\n]*")
POM_TEXTS = re.compile(r"<(name|description)>[\s\S]*?</\1>")
H2_DATABASE = re.compile(r"jdbc:h2:mem:[\w.-]+")
WHITESPACE = re.compile(r"\s+")


def without_comments(text, suffix):

    kind = COMMENTS_BY_SUFFIX.get(suffix)
    if kind in ("c", "c-and-xml"):
        text = C_COMMENT.sub(lambda match: match.group(1) or "", text)
    if kind in ("xml", "c-and-xml"):
        text = XML_COMMENT.sub("", text)
    if kind == "hash":
        text = HASH_COMMENT.sub(r"\1", text)
    if kind == "sql":
        text = SQL_COMMENT.sub(r"\1", text)
    return text


def code_of(file, own_id):
    """What the file says once names, comments and whitespace are taken out."""

    data = file.read_bytes()
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        return data
    text = without_comments(text, file.suffix)
    if file.name == "pom.xml":
        text = POM_TEXTS.sub("", text)
    text = text.replace(own_id, "<blueprint-id>")
    text = H2_DATABASE.sub("jdbc:h2:mem:<database>", text)
    return WHITESPACE.sub(" ", text).strip()


def files_of(directory):
    """The files git knows below the directory, or would add, as paths relative to it."""

    listed = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "."],
        cwd=directory, capture_output=True, check=True, text=True,
    ).stdout
    return {
        path for path in listed.split("\0")
        if path and (directory / path).is_file()
    }


def allowed(path):
    return any(fnmatch.fnmatch(path, pattern) for pattern in ALLOWED)


def names_in_table(readme):
    """What the backticks of the first cells of the delta table name."""

    text = readme.read_text(encoding="utf-8")
    start = text.find(DELTA_HEADING)
    if start < 0:
        return None
    section = text[start + len(DELTA_HEADING):]
    following = re.search(r"^## ", section, re.MULTILINE)
    if following:
        section = section[:following.start()]
    names = []
    for _, cells in file_table_rows(section):
        for token in CODE_SPAN.findall(cells[0]):
            names.append(token)
    return names


def named(path, names):
    """Whether one of the names of the table covers the path."""

    file = PurePosixPath(path)
    for name in names:
        # fnmatch lets '*' match a '/' as well. So '**/' becomes '*', which also matches
        # no directory at all, as a reader of 'templates/**/*.ftl' expects.
        pattern = as_pattern(name).replace("**/", "*")
        if name.endswith("/"):
            # A directory names every file below it, wherever the directory is.
            if fnmatch.fnmatch(path, pattern + "/*") or fnmatch.fnmatch(path, f"*/{pattern}/*"):
                return True
            continue
        if "/" not in pattern:
            if "openapi" in file.parts:
                continue
            if fnmatch.fnmatch(file.name, pattern) or fnmatch.fnmatch(file.stem, pattern):
                return True
            continue
        if fnmatch.fnmatch(path, pattern) or fnmatch.fnmatch(path, "*/" + pattern):
            return True
    return False


def unnamed_differences(blueprint, base, own_id, base_id, names):

    own_files = {path for path in files_of(blueprint) if not allowed(path)}
    base_files = {
        path.replace(base_id, own_id) for path in files_of(base) if not allowed(path)
    }
    unnamed = []
    for path in sorted(own_files | base_files):
        if path not in base_files:
            reason = "only in this blueprint"
        elif path not in own_files:
            reason = "only in the base"
        else:
            base_path = base / path.replace(own_id, base_id)
            if not base_path.exists():
                base_path = base / path
            if code_of(blueprint / path, own_id) == code_of(base_path, base_id):
                continue
            reason = "different"
        if not named(path, names):
            unnamed.append((path, reason))
    return unnamed


def main():

    if len(sys.argv) != 2:
        sys.exit("Usage: bin/check_delta_tables.py <blueprints.yaml>")
    index = yaml.safe_load(Path(sys.argv[1]).read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent.parent

    bases = {
        entry["id"]: entry["base"]
        for entry in index.get("blueprints", [])
        if entry.get("base")
    }
    directories = {
        (path.parent.name, path.name): path
        for path in root.glob("*/*/*")
        if path.is_dir() and path.name in PLATFORMS
    }

    problems, compared = [], 0
    for (own_id, platform), blueprint in sorted(directories.items()):
        base_id = bases.get(own_id)
        base = directories.get((base_id, platform)) if base_id else None
        if base is None:
            continue
        compared += 1
        readme = blueprint / "README.md"
        display = readme.relative_to(root)
        names = names_in_table(readme) if readme.exists() else None
        if names is None:
            problems.append(f"{display}: has no section '{DELTA_HEADING}'")
            continue
        for path, reason in unnamed_differences(blueprint, base, own_id, base_id, names):
            problems.append(f"{display}: does not name '{path}' ({reason})")

    if problems:
        print("Delta tables leave out files which differ from the base:\n", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print(
            f"\n{len(problems)} problem(s) found. Name each file in the table under"
            f" '{DELTA_HEADING}', or in a row which groups it with others. A file which"
            " differs in every blueprint and is no delta belongs on the list ALLOWED of"
            " this script, with the reason.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"delta tables are complete: {compared} blueprint(s) compared with their base")


if __name__ == "__main__":
    main()
