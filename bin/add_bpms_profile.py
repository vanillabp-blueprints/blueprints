#!/usr/bin/env python3
"""Give every blueprint which runs on Camunda 7 a Maven profile for an engine forked from it.

Operaton and CIB seven are forks of Camunda 7. Both run embedded, read the BPMN files of
Camunda 7 as they are, and have a VanillaBP adapter with the same configuration keys. So a
blueprint gets such an engine the same way every time, and this script does it:

  - every POM with a 'camunda7' profile gets a profile of the new type next to it. Each
    adapter artifact of Camunda 7 is replaced by the artifact of the same name of the new
    adapter, with the same scope,
  - the POM which holds '<camunda7-adapter.version>' gets the version of the new adapter
    next to it,
  - every 'application-camunda7.yaml' gets an 'application-<type>.yaml' next to it, with
    the adapter id, the names in the comments and the path of the web applications changed,
    and with the settings the web applications of the engine need on top, if it has any,
  - every directory 'processes/camunda7' is copied to 'processes/<type>', because the BPMN
    files are read from a directory per adapter id,
  - the README of the blueprint gets a paragraph after the one which explains the
    'camunda8' profile.

A POM whose 'camunda7' profile holds more than the adapter, a build section for example, is
left alone and reported. So are a README without that paragraph and a profile file which
still names Camunda 7 after the replacements. These are the places a person has to look at.
A blueprint which already has the profile is skipped, so the script can run again.

Usage: bin/add_bpms_profile.py <type> [<blueprint directory> ...]
       <type> is one of the keys of ENGINES. Without directories, every
       '<group>/<blueprint-id>/<platform>/' of the repository is handled.
"""

import re
import shutil
import sys
from pathlib import Path

PLATFORMS = ("springboot", "quarkus")

SOURCE = "camunda7"

# What differs between the engines this script knows. A new fork is a new entry.
ENGINES = {
    "operaton": {
        "name": "Operaton",
        "group_id": "io.vanillabp",
        "artifact_prefix": "operaton-adapter",
        "version": "2.0.0-SNAPSHOT",
        "webapps_path": "/operaton",
        "readme": (
            "[Operaton](https://operaton.org), the open source successor of Camunda 7, is"
            " embedded as well\nand needs nothing else either. The BPMN files are the ones"
            " of Camunda 7, because Operaton reads\ntheir `camunda:` attributes:\n\n"
            "```bash\nmvn install verify -Poperaton\n```\n"
        ),
    },
    "cibseven": {
        "name": "CIB seven",
        "group_id": "io.vanillabp",
        "artifact_prefix": "cibseven-adapter",
        "version": "2.0.0-2.2-SNAPSHOT",
        # CIB seven kept the path of Camunda 7.
        "webapps_path": "/camunda",
        "readme": (
            "[CIB seven](https://cibseven.org), a fork of Camunda 7 made by CIB software, is"
            " embedded as\nwell and needs nothing else either. The BPMN files are the ones of"
            " Camunda 7, because CIB seven\nreads their `camunda:` attributes:\n\n"
            "```bash\nmvn install verify -Pcibseven\n```\n"
        ),
        # The webapps module of CIB seven also starts CIB seven's new webclient, and that one
        # does not start without a secret for its login tokens.
        "webapps_settings": (
            "\n"
            "# CIB seven's new webclient at /webapp comes with the web applications. It signs its\n"
            "# login tokens with this secret and does not start without one. The value is for the\n"
            "# demo only. A real application sets its own, the same on every instance, for example\n"
            "# from an environment variable. Make one with: openssl rand -base64 120 | tr -d '\\n'\n"
            "cibseven:\n"
            "  webclient:\n"
            "    authentication:\n"
            "      jwtSecret: oZTyrJ3x7k5b7XHJplVywCce/uIHDPTz1r4UYZkRhh7RZmJjEhalX0V0cyf0+ceGj/0ynEoq"
            "+yOslcudCUyzCT9Jt3lrteFn9ZfeIUY8NIR84cd5rzOLg0te86nmVGmoNqiezm2wZ6Xk797SeoYGiNp+eY1nbNkm\n"
        ),
    },
}

# The paragraph which explains the camunda8 profile, as the READMEs word it. The new paragraph
# goes after the first one found.
README_ANCHORS = (
    "The file is loaded because the Maven profile `camunda8`",
    "The file applies because the Maven profile `camunda8`",
    "Camunda 8 is a remote engine, so a cluster has to run.",
    "Camunda 8 is remote, so a cluster has to run",
)

# Paragraphs which still speak about Camunda 8 and follow the one above in some READMEs. The new
# paragraph goes after them, so they keep standing next to what they refer to.
README_CONTINUATIONS = (
    "Take the address out and the application does not boot.",
    "Without it the application does not boot.",
    "The test which cancels the user task does not run there.",
    "That engine has no loop cardinality.",
)

WEBAPPS_COMMENT = """        <!--
          {name}'s Cockpit, Tasklist and Admin at {path}. Optional, like the web
          applications of the camunda7 profile.
        -->
"""


def profile_block(text, profile_id):
    """:return: the '<profile>' element of that id with its indentation, or None"""

    match = re.search(
        r"( *)<profile>\s*<id>" + re.escape(profile_id) + r"</id>.*?</profile>\n", text, re.S
    )
    return match


def only_the_adapter(profile):
    """Whether a camunda7 profile holds nothing but the BPMS property and the adapter."""

    stripped = re.sub(r"<!--.*?-->", "", profile, flags=re.S)
    tags = set(re.findall(r"<([\w.-]+)>", stripped))
    allowed = {
        "profile", "id", "activation", "activeByDefault", "properties", "bpms",
        "blueprint.bpms", "dependencies", "dependency", "groupId", "artifactId", "version",
        "scope",
    }
    return tags <= allowed


def new_profile(profile, engine_type, engine):
    """The profile of the new engine, built from the camunda7 one."""

    artifacts = re.findall(
        r"<artifactId>(camunda7-adapter[\w-]*)</artifactId>\s*"
        r"<version>[^<]*</version>\s*(?:<scope>(\w+)</scope>)?",
        profile,
    )
    lines = [
        "    <profile>",
        f"      <id>{engine_type}</id>",
        "      <properties>",
        f"        <bpms>{engine_type}</bpms>",
    ]
    if "<blueprint.bpms>" in profile:
        lines.append(f"        <blueprint.bpms>{engine_type}</blueprint.bpms>")
    lines += ["      </properties>", "      <dependencies>"]
    for artifact, scope in artifacts:
        name = engine["artifact_prefix"] + artifact[len("camunda7-adapter"):]
        if name.endswith("-webapps"):
            lines.append(
                WEBAPPS_COMMENT.format(name=engine["name"], path=engine["webapps_path"]).rstrip("\n")
            )
        lines += [
            "        <dependency>",
            f"          <groupId>{engine['group_id']}</groupId>",
            f"          <artifactId>{name}</artifactId>",
            f"          <version>${{{engine['artifact_prefix']}.version}}</version>",
        ]
        if scope:
            lines.append(f"          <scope>{scope}</scope>")
        lines.append("        </dependency>")
    lines += ["      </dependencies>", "    </profile>"]
    return "\n".join(lines) + "\n"


def add_profile(pom, engine_type, engine, report):

    text = pom.read_text(encoding="utf-8")
    if f"<id>{engine_type}</id>" in text:
        return
    match = profile_block(text, SOURCE)
    if match is None:
        return
    if not only_the_adapter(match.group(0)):
        report.append(f"{pom}: the camunda7 profile holds more than the adapter, add it by hand")
        return
    position = text.rindex("  </profiles>")
    text = text[:position] + new_profile(match.group(0), engine_type, engine) + text[position:]
    pom.write_text(text, encoding="utf-8")


def add_version(pom, engine_type, engine):

    text = pom.read_text(encoding="utf-8")
    key = f"<{engine['artifact_prefix']}.version>"
    if key in text:
        return
    match = re.search(r"( *)<camunda8-adapter\.version>[^<]*</camunda8-adapter\.version>\n", text)
    if match is None:
        match = re.search(r"( *)<camunda7-adapter\.version>[^<]*</camunda7-adapter\.version>\n", text)
    if match is None:
        return
    line = f"{match.group(1)}{key}{engine['version']}</{engine['artifact_prefix']}.version>\n"
    text = text[: match.end()] + line + text[match.end():]
    pom.write_text(text, encoding="utf-8")


def profile_file(source, engine_type, engine, report):

    target = source.with_name(f"application-{engine_type}.yaml")
    if target.exists():
        return
    text = source.read_text(encoding="utf-8")
    name = engine["name"]
    text = text.replace("Camunda's web applications", f"{name}'s web applications")
    text = text.replace("Camunda 7", name)
    text = re.sub(r"/camunda\b", engine["webapps_path"], text)
    text = text.replace("'camunda7'", f"'{engine_type}'")
    text = re.sub(r"^(\s*)camunda7:", rf"\g<1>{engine_type}:", text, flags=re.M)
    if "webapps_settings" in engine and re.search(r"^\s+webapps:", text, flags=re.M):
        text = text.rstrip("\n") + "\n" + engine["webapps_settings"]
    target.write_text(text, encoding="utf-8")
    if re.search(r"camunda7|Camunda", text):
        report.append(f"{target}: still names Camunda, check it by hand")


def process_directories(directory, engine_type):

    for models in sorted(directory.rglob("camunda7")):
        if not models.is_dir() or "target" in models.parts:
            continue
        if models.parent.name != "processes" and models.parent.name != "version-1":
            continue
        target = models.with_name(engine_type)
        if not target.exists():
            shutil.copytree(models, target)


def readme(directory, engine_type, engine, report):

    path = directory / "README.md"
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    if f"-P{engine_type}" in text:
        return
    starts = [text.find(anchor) for anchor in README_ANCHORS if anchor in text]
    start = starts[0] if starts else -1
    if start < 0:
        report.append(f"{path}: no paragraph about the camunda8 profile, add the {engine_type} one by hand")
        return
    end = text.find("\n\n", start) + 2
    while text.startswith(README_CONTINUATIONS, end):
        end = text.find("\n\n", end) + 2
    # the paragraphs of the other forks stay in the order they were added
    for other in ENGINES.values():
        if text.startswith(other["readme"] + "\n", end):
            end += len(other["readme"]) + 1
    text = text[:end] + engine["readme"] + "\n" + text[end:]
    path.write_text(text, encoding="utf-8")


def blueprint_directories(root):

    for pom in sorted(root.glob("*/*/*/pom.xml")):
        if pom.parent.name in PLATFORMS:
            yield pom.parent


def main():

    if len(sys.argv) < 2 or sys.argv[1] not in ENGINES:
        print(__doc__, file=sys.stderr)
        return 2
    engine_type = sys.argv[1]
    engine = ENGINES[engine_type]
    root = Path(__file__).resolve().parent.parent
    directories = [Path(argument).resolve() for argument in sys.argv[2:]] or list(
        blueprint_directories(root)
    )

    report = []
    for directory in directories:
        poms = [pom for pom in sorted(directory.rglob("pom.xml")) if "target" not in pom.parts]
        if not any(f"<id>{SOURCE}</id>" in pom.read_text(encoding="utf-8") for pom in poms):
            continue
        for pom in poms:
            add_profile(pom, engine_type, engine, report)
            if "<camunda7-adapter.version>" in pom.read_text(encoding="utf-8"):
                add_version(pom, engine_type, engine)
        for source in sorted(directory.rglob(f"application-{SOURCE}.yaml")):
            if "target" not in source.parts:
                profile_file(source, engine_type, engine, report)
        process_directories(directory, engine_type)
        readme(directory, engine_type, engine, report)

    for line in report:
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
