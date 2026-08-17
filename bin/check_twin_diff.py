#!/usr/bin/env python3
"""Compare the Java sources of a blueprint's platform twins.

VanillaBP application code is platform-invariant in everything but the platform's own
annotations and APIs. The twins of a blueprint are supposed to prove that continuously, so
this script compares them and fails on every difference which is not approved.

  <group>/<blueprint-id>/springboot/**/src/{main,test}/java/**/*.java
  <group>/<blueprint-id>/quarkus/**/src/{main,test}/java/**/*.java

Compared is what the code says, not how it is written down: package and import lines and
comments are removed before comparing, so a different import or a Javadoc explaining one
platform never shows up as a difference. What remains - types, signatures, annotations and
bodies - has to be identical or approved.

Approved differences live in '<group>/<blueprint-id>/twin-diff-allow.txt', one line each:

    <hash>  <kind>  <path>  # why the twins differ here

'kind' is 'differs' (the file exists in both twins), 'only-springboot' or 'only-quarkus'.
The hash covers the difference itself, not the file: changing both twins the same way keeps
it, changing one of them does not. A difference which grows therefore comes back for review
instead of hiding behind an approval given once.

A blueprint which has only one of the two platform directories is a failure as well, and
that needs the index: a platform is allowed to be missing only where the index says
'not-applicable' for it, which is a statement about the platform and carries its reason.
Without the index this stays a message rather than a failure, so a local run works offline.

Usage: bin/check_twin_diff.py [blueprints.yaml]
                                         fail on every unapproved difference (CI)
       bin/check_twin_diff.py --update   write the approvals, keeping the reasons already
                                         given; new entries get a reason to fill in
"""

import hashlib
import sys
from difflib import unified_diff
from pathlib import Path

PLATFORMS = ("springboot", "quarkus")

ALLOW_FILE = "twin-diff-allow.txt"

TODO_REASON = "TODO: say why the twins differ here"

HEADER = """\
# Approved differences between the platform twins of this blueprint, checked by
# bin/check_twin_diff.py. One line per file: the hash of the difference, what kind of
# difference it is, the file, and why it exists. Write the reason - the file is read by
# people deciding whether a difference is the platform or a mistake.
"""


def java_sources(platform_directory):
    """Every Java source of a platform twin, keyed by its path inside the twin."""

    sources = {}
    for java in sorted(platform_directory.glob("**/src/*/java/**/*.java")):
        sources[java.relative_to(platform_directory).as_posix()] = java
    return sources


def code_of(java):
    """The file without package, imports and comments - what the code actually says."""

    text = java.read_text(encoding="utf-8")

    stripped, index, length = [], 0, len(text)
    in_line_comment = in_block_comment = False
    in_string = in_char = False
    while index < length:
        character = text[index]
        pair = text[index:index + 2]

        if in_line_comment:
            if character == "\n":
                in_line_comment = False
                stripped.append(character)
            index += 1
            continue
        if in_block_comment:
            if pair == "*/":
                in_block_comment = False
                index += 2
                continue
            if character == "\n":
                stripped.append(character)
            index += 1
            continue
        if in_string or in_char:
            stripped.append(character)
            if character == "\\":
                if index + 1 < length:
                    stripped.append(text[index + 1])
                index += 2
                continue
            if in_string and character == '"':
                in_string = False
            elif in_char and character == "'":
                in_char = False
            index += 1
            continue

        if pair == "//":
            in_line_comment = True
            index += 2
            continue
        if pair == "/*":
            in_block_comment = True
            index += 2
            continue
        if character == '"':
            in_string = True
        elif character == "'":
            in_char = True
        stripped.append(character)
        index += 1

    lines = []
    for line in "".join(stripped).splitlines():
        line = line.rstrip()
        if not line.strip():
            continue
        if line.startswith("package ") or line.startswith("import "):
            continue
        lines.append(line)
    return lines


def difference_of(blueprint, path, springboot, quarkus):
    """The difference of one file as (kind, diff text), or None if there is none."""

    if springboot is None or quarkus is None:
        present = "quarkus" if springboot is None else "springboot"
        java = quarkus if springboot is None else springboot
        return f"only-{present}", "\n".join(code_of(java))

    left, right = code_of(springboot), code_of(quarkus)
    if left == right:
        return None
    diff = unified_diff(
        left, right,
        fromfile=f"{blueprint}/springboot/{path}",
        tofile=f"{blueprint}/quarkus/{path}",
        lineterm="", n=1)
    # the header lines carry the paths, which the entry names anyway
    body = [line for line in diff if not line.startswith(("---", "+++"))]
    return "differs", "\n".join(body)


def hash_of(text):

    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def differences_of(blueprint_directory):
    """Every difference between the twins of one blueprint, keyed by its file."""

    springboot = java_sources(blueprint_directory / "springboot")
    quarkus = java_sources(blueprint_directory / "quarkus")

    found = {}
    for path in sorted(set(springboot) | set(quarkus)):
        difference = difference_of(
            blueprint_directory.name, path, springboot.get(path), quarkus.get(path))
        if difference is not None:
            kind, text = difference
            found[path] = (kind, hash_of(text), text)
    return found


def approvals_of(blueprint_directory):
    """The approved differences, keyed by file: (hash, kind, reason)."""

    allow_file = blueprint_directory / ALLOW_FILE
    if not allow_file.is_file():
        return {}

    approved = {}
    for line in allow_file.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        entry, _, reason = line.partition("#")
        parts = entry.split()
        if len(parts) != 3:
            print(f"{allow_file}: cannot read the entry '{line.strip()}'", file=sys.stderr)
            continue
        digest, kind, path = parts
        approved[path] = (digest, kind, reason.strip())
    return approved


def write_approvals(blueprint_directory, differences, approved):
    """Writes the approvals, keeping every reason already given."""

    lines = [HEADER]
    for path, (kind, digest, _) in differences.items():
        previous = approved.get(path)
        reason = previous[2] if (previous is not None) and previous[2] else TODO_REASON
        lines.append(f"{digest}  {kind}  {path}  # {reason}")
    (blueprint_directory / ALLOW_FILE).write_text("\n".join(lines) + "\n", encoding="utf-8")


def blueprints(root):
    """Every blueprint directory, with the platforms it actually has."""

    found = {}
    for directory in sorted(root.glob("*/*")):
        if not directory.is_dir():
            continue
        platforms = tuple(
            platform
            for platform in PLATFORMS
            if (directory / platform / "pom.xml").is_file())
        if platforms:
            found[directory] = platforms
    return found


def inapplicable_platforms(index_file):
    """The platforms the index declares 'not-applicable', as (blueprint id, platform).

    Returns None if no index was given: a local run without it still compares the twins
    it finds, it only cannot tell a missing twin from one which may not exist.
    """

    if index_file is None:
        return None

    import yaml

    index = yaml.safe_load(index_file.read_text(encoding="utf-8"))
    return {
        (blueprint["id"], platform)
        for blueprint in index["blueprints"]
        for platform in PLATFORMS
        if blueprint["platforms"][platform]["status"] == "not-applicable"
    }


def main():

    arguments = sys.argv[1:]
    update = "--update" in arguments
    given = [argument for argument in arguments if not argument.startswith("--")]
    root = Path(__file__).resolve().parent.parent

    index_file = Path(given[0]) if given else None
    inapplicable = inapplicable_platforms(index_file)

    directories = blueprints(root)
    if not directories:
        print("no blueprint yet - nothing to compare")
        return 0

    failures, unpaired, compared = [], [], 0
    for blueprint_directory, platforms in directories.items():
        display = blueprint_directory.relative_to(root)

        if len(platforms) < len(PLATFORMS):
            missing = [
                platform for platform in PLATFORMS if platform not in platforms]
            allowed = [
                platform
                for platform in missing
                if inapplicable is not None
                and (blueprint_directory.name, platform) in inapplicable]
            if inapplicable is None:
                print(
                    f"{display}: only '{platforms[0]}' exists."
                    " Pass blueprints.yaml to have this checked against the index")
            elif sorted(allowed) == sorted(missing):
                print(
                    f"{display}: only '{platforms[0]}' exists, and the index says"
                    f" '{', '.join(missing)}' is not applicable to this blueprint")
            else:
                unpaired.append((display, missing))
            continue

        differences = differences_of(blueprint_directory)
        approved = approvals_of(blueprint_directory)
        compared += 1

        if update:
            write_approvals(blueprint_directory, differences, approved)
            print(f"{display}: {len(differences)} approved difference(s) written")
            continue

        for path, (kind, digest, text) in differences.items():
            previous = approved.get(path)
            if previous is None:
                failures.append((display, path, kind, digest, text, "not approved"))
            elif previous[0] != digest:
                failures.append((display, path, kind, digest, text, "changed since it was approved"))
            elif previous[1] != kind:
                failures.append((display, path, kind, digest, text, f"approved as '{previous[1]}'"))
            elif not previous[2]:
                failures.append((display, path, kind, digest, text, "approved without a reason"))

        for path, (digest, kind, _) in approved.items():
            if path not in differences:
                failures.append((
                    display, path, kind, digest, "",
                    "approved although the twins do not differ here any more"))

    if update:
        return 0

    for display, path, kind, digest, text, why in failures:
        print(f"\n{display}: {path} ({kind}) - {why}")
        if text:
            print("\n".join(f"    {line}" for line in text.splitlines()[:40]))
        print(
            f"  approve it by adding to {display}/{ALLOW_FILE}:\n"
            f"    {digest}  {kind}  {path}  # <why the twins differ here>")

    for display, missing in unpaired:
        print(
            f"\n{display}: '{', '.join(missing)}' does not exist, so there is nothing to"
            " compare."
            "\n  A blueprint exists for both platforms. The one exception is a platform"
            " which does not know the subject of the blueprint at all - then blueprints.yaml"
            f" says 'not-applicable' for it, with the reason, and the organisation page shows"
            " that reason where the other platforms show their link.")

    if failures or unpaired:
        print(
            f"\n{len(failures)} unapproved difference(s) and {len(unpaired)} blueprint(s)"
            " existing for one platform only.",
            file=sys.stderr)
        if failures:
            print(
                "Run 'bin/check_twin_diff.py --update' and write the reasons.",
                file=sys.stderr)
        return 1

    print(f"platform twins agree: {compared} blueprint(s) compared")
    return 0


if __name__ == "__main__":
    sys.exit(main())
