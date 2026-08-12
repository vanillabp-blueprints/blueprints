#!/usr/bin/env python3
"""Check that every blueprint documents itself the way all the others do.

A blueprint carries a README.md for humans and an AGENTS.md for AI agents. Both have a
fixed section structure, which is what makes them predictable: an agent which has read one
AGENTS.md knows where to look in all of them.

Checked per blueprint directory '<blueprint-id>/<platform>/':

  - README.md and AGENTS.md exist,
  - they contain the required '##' sections, in the defined order,
  - they contain no section outside the defined set,
  - they do not mention the other platform - a Spring Boot blueprint does not know that
    Quarkus exists, and the other way round,
  - the README points back at the monorepo: a blueprint repository is a read-only mirror,
    and a reader who found a bug has to know where the issue belongs,
  - the README shows the process in its first section, and the picture it points at is
    there. A BPMN file is not something a reader reads (bin/render_bpmn_images.sh),
  - LICENSE, NOTICE and .gitignore are there and are the ones of the repository root. The
    split turns the directory into a repository, and the root files do not come along.

The templates in templates/ are checked as well, minus the platform rule.

Usage: bin/check_docs_structure.py [blueprints.yaml]
       Passing the index makes the section 'Delta to the base blueprint' required for
       every blueprint which has a 'base'; without it the section stays optional.
"""

import re
import sys
from pathlib import Path

PLATFORMS = ("springboot", "quarkus")

# (section, required) in the order they have to appear.
SECTIONS = {
    "README.md": [
        ("What this blueprint shows", True),
        ("Delta to the base blueprint", False),
        ("Running it", True),
        ("How it works", True),
        ("Documentation", True),
        # The footer every VanillaBP repository carries. A split blueprint is a repository
        # of its own, so it says who wrote this and under which license it may be used.
        ("Noteworthy & Contributors", True),
        ("License", True),
    ],
    "AGENTS.md": [
        ("Placeholders", True),
        ("Core files", True),
        ("Boilerplate files", True),
        ("Adding this blueprint to an existing project", True),
        ("Verifying", True),
    ],
}

# What a blueprint of one platform must not mention.
FOREIGN_PLATFORM = {
    "springboot": re.compile(r"\bquarkus\b", re.IGNORECASE),
    "quarkus": re.compile(r"\bspring[ -]?boot\b", re.IGNORECASE),
}

SECTION_PATTERN = re.compile(r"^## +(.+?)\s*$", re.MULTILINE)

# Files a blueprint carries although the monorepo has them at its root: the split makes a
# repository out of the directory alone, and a repository without its license is not one
# anybody may use. Their content is the root's, byte for byte.
REPOSITORY_FILES = ("LICENSE", "NOTICE", ".gitignore")

# The split repositories are mirrors; this is how a reader learns where to file an issue.
MONOREPO_URL = "https://github.com/vanillabp-blueprints/blueprints"

# The picture of the process, rendered by bin/render_bpmn_images.sh.
IMAGE_PATTERN = re.compile(r"!\[[^\]]*\]\(([^)\s]+)\)")
FIRST_SECTION = "What this blueprint shows"


def check_file(path, filename, required_sections, platform, errors, root):
    display = path.relative_to(root)
    if not path.exists():
        errors.append(f"{display}: is missing")
        return

    text = path.read_text(encoding="utf-8")
    found = SECTION_PATTERN.findall(text)
    known = [section for section, _ in SECTIONS[filename]]

    for section in found:
        if section not in known:
            errors.append(
                f"{display}: unknown section '## {section}'"
                f" (allowed: {', '.join(known)})"
            )
    for section in sorted({s for s in found if found.count(s) > 1}):
        errors.append(f"{display}: section '## {section}' occurs more than once")

    expected = [
        section
        for section, required in SECTIONS[filename]
        if required or section in found
    ]
    for section in required_sections:
        if section not in found:
            errors.append(f"{display}: section '## {section}' is missing")

    in_order = [section for section in found if section in expected]
    if in_order != [section for section in expected if section in found]:
        errors.append(
            f"{display}: sections are out of order -"
            f" expected {' < '.join(expected)}, found {' < '.join(in_order)}"
        )

    if platform is not None and filename == "README.md" and MONOREPO_URL not in text:
        errors.append(
            f"{display}: does not link {MONOREPO_URL}. A blueprint repository is a"
            " read-only mirror and has to say where issues belong."
        )

    if platform is not None:
        for match in FOREIGN_PLATFORM[platform].finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            errors.append(
                f"{display}:{line}: mentions '{match.group(0)}' - a {platform} blueprint"
                " must not refer to the other platform"
            )


def check_process_picture(directory, errors, root):
    """The README shows the process before it explains it."""
    readme = directory / "README.md"
    if not readme.exists():
        return

    display = readme.relative_to(root)
    text = readme.read_text(encoding="utf-8")

    heading = re.search(rf"^## +{re.escape(FIRST_SECTION)}\s*$", text, re.MULTILINE)
    if not heading:
        return
    following = SECTION_PATTERN.search(text, heading.end())
    section = text[heading.end() : following.start() if following else len(text)]

    images = IMAGE_PATTERN.findall(section)
    if not images:
        errors.append(
            f"{display}: section '## {FIRST_SECTION}' shows no picture of the process -"
            " render it with bin/render_bpmn_images.sh and reference it as"
            " '![...](docs/<process-id>.png)'"
        )
        return

    for image in images:
        if image.startswith(("http://", "https://", "/")):
            errors.append(
                f"{display}: references the picture as '{image}'. It has to be a relative"
                " path, otherwise it breaks in the split repository"
            )
        elif not (directory / image).exists():
            errors.append(
                f"{display}: references '{image}', which is not there -"
                " run bin/render_bpmn_images.sh"
            )


def check_repository_files(directory, errors, root):
    """The files the split repository needs and only the root has."""
    for filename in REPOSITORY_FILES:
        copy = directory / filename
        display = copy.relative_to(root)
        reference = root / filename
        if not copy.exists():
            errors.append(
                f"{display}: is missing. After the split this directory is a repository"
                f" of its own - run 'cp {filename} {directory.relative_to(root)}/'"
            )
        elif copy.read_bytes() != reference.read_bytes():
            errors.append(
                f"{display}: differs from {filename} of the repository root -"
                f" run 'cp {filename} {directory.relative_to(root)}/'"
            )


def main():
    root = Path(__file__).resolve().parent.parent
    bases = set()
    if len(sys.argv) > 1:
        import yaml

        index = yaml.safe_load(Path(sys.argv[1]).read_text(encoding="utf-8"))
        bases = {
            blueprint["id"]
            for blueprint in index["blueprints"]
            if blueprint.get("base")
        }

    errors = []
    checked = 0

    for filename, sections in SECTIONS.items():
        # The templates carry every section, including the optional one.
        check_file(
            root / "templates" / filename,
            filename,
            [section for section, _ in sections],
            None,
            errors,
            root,
        )

    for pom in sorted(root.glob("*/*/pom.xml")):
        directory = pom.parent
        blueprint_id, platform = directory.parent.name, directory.name
        if platform not in PLATFORMS:
            continue
        checked += 1
        for filename, sections in SECTIONS.items():
            required = [section for section, required in sections if required]
            if filename == "README.md" and blueprint_id in bases:
                required.append("Delta to the base blueprint")
            check_file(
                directory / filename, filename, required, platform, errors, root
            )
        check_process_picture(directory, errors, root)
        check_repository_files(directory, errors, root)

    if errors:
        print("Blueprint documentation does not follow the templates:\n", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        print(f"\n{len(errors)} problem(s) found.", file=sys.stderr)
        sys.exit(1)

    print(f"documentation structure is fine: 2 templates, {checked} blueprints")


if __name__ == "__main__":
    main()
