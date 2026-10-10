#!/usr/bin/env python3
"""Check that the BPMS profiles of a blueprint agree on how identifiers are scoped.

'name-clash-avoidance' decides what an adapter calls a BPMN process, a message, a signal
and an error in the BPMS. Under 'use-prefix' the workflow module id goes in front of the
name and no tenant is used; under 'by-adapter', which is the DEFAULT, the plain names go
into a tenant named after the module. Changing that while an application runs is a
migration and not a setting: the identifiers the BPMS knows would change, and workflows
started earlier would no longer be found.

A blueprint configures one profile file per BPMS, and a migration blueprint runs one of
them after the other. Two profile files which leave ONE adapter under two different modes
therefore describe a switch nobody survives. That is what module-bpms-migration did: its
'camunda7' profile set 'use-prefix', its 'camunda8' profile named no mode for 'camunda7'
and so fell back to the default, and after the switch a message for a loan approval
started before it correlated against nothing. Measured on the 28th of September 2026, and
no test saw it.

Which is why the DEFAULT counts here. A file which says nothing is not a file with no
opinion: it is a file which says 'by-adapter'. Leaving the setting out of one of two
profiles is the whole defect this check is about.

The same holds between the application and the tests. The module test of a blueprint runs
under the same BPMS profile as the application, but it reads the profile files of its own
source root. Where that root has no file for a profile, the test runs that profile with
the default mode, and then it tests a configuration the blueprint does not ship. That is
what the module test of module-bpms-migration did in its 'camunda7' profile, found on the
28th of September 2026 as well.

Checked for every '<group>/<blueprint-id>/<platform>/', across all of its source roots:

  - a source root takes part when it has an 'application.yaml' or an
    'application-<profile>.yaml',
  - a profile is every '<profile>' one of these roots has a file for,
  - for each root and each profile, 'application.yaml' of that root is the base and
    'application-<profile>.yaml' of that root is read on top of it, which is what the
    platforms do; a root without that file runs the profile on its base alone,
  - an adapter is IN USE in a profile when one root configures it under
    'vanillabp.adapters' for that profile or names it in a 'prioritized-adapters' list
    anywhere, and then it is in use in every root which runs that profile,
  - its mode is what the root names for it, or 'by-adapter',
  - an adapter left under different modes anywhere in one blueprint fails, whether the
    two places are two profiles or the application and a test.

Usage: bin/check_scoping_modes.py
"""

import sys
from collections import defaultdict
from pathlib import Path

import yaml

PLATFORMS = ("springboot", "quarkus")

BASE_FILE = "application.yaml"

PROFILE_PREFIX = "application-"

PROFILE_FILE = f"{PROFILE_PREFIX}*.yaml"

SETTING = "name-clash-avoidance"

# What an adapter does when no profile names the setting. It is the mode VanillaBP 1
# deployed, which is why it is the default, and it is not the mode most blueprints want.
DEFAULT_MODE = "by-adapter"


def merged(base, overlay):
    """Overlay on top of base, the way a platform reads a profile file over the base one."""

    if not isinstance(base, dict) or not isinstance(overlay, dict):
        return overlay if overlay is not None else base
    result = dict(base)
    for key, value in overlay.items():
        result[key] = merged(base.get(key), value) if key in base else value
    return result


def prioritized_adapters(node):
    """Every adapter id named in a 'prioritized-adapters' list anywhere below this node."""

    found = set()
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "prioritized-adapters" and isinstance(value, list):
                found.update(str(entry) for entry in value)
            else:
                found.update(prioritized_adapters(value))
    elif isinstance(node, list):
        for entry in node:
            found.update(prioritized_adapters(entry))
    return found


def adapter_settings(document):
    """The 'vanillabp' section and the adapters configured in it."""

    vanillabp = (document or {}).get("vanillabp")
    if not isinstance(vanillabp, dict):
        return {}, {}
    adapters = vanillabp.get("adapters")
    return vanillabp, adapters if isinstance(adapters, dict) else {}


def adapters_in_use(document):
    """The adapter ids this configuration names, as a set."""

    vanillabp, adapters = adapter_settings(document)
    return set(adapters) | prioritized_adapters(vanillabp)


def mode_of(document, adapter):
    """The mode this configuration leaves the adapter under: the one it names, or the default."""

    _, adapters = adapter_settings(document)
    settings = adapters.get(adapter)
    named = settings.get(SETTING) if isinstance(settings, dict) else None
    return str(named) if named is not None else DEFAULT_MODE


def load(path):
    """A YAML file as a dictionary, or an empty one where the file does not exist."""

    if not path.exists():
        return {}
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def check_blueprint(blueprint, errors, root):
    """Compares every profile of every source root of one blueprint.

    Returns how many source roots and how many profile files it read.
    """

    sources = [
        resources for resources in sorted(blueprint.glob("*/src/*/resources"))
        if (resources / BASE_FILE).exists() or any(resources.glob(PROFILE_FILE))
    ]
    profiles = sorted({
        profile.stem[len(PROFILE_PREFIX):]
        for resources in sources
        for profile in resources.glob(PROFILE_FILE)
    })

    # what each root runs in each profile, and where a reader finds it
    documents = {}
    in_use = defaultdict(set)
    files = 0
    for resources in sources:
        base = load(resources / BASE_FILE)
        for profile in profiles:
            profile_file = resources / f"{PROFILE_PREFIX}{profile}.yaml"
            if profile_file.exists():
                files += 1
                where = f"{profile_file.relative_to(root)}"
            else:
                where = (f"{resources.relative_to(root)} (no {profile_file.name}, so"
                         f" profile '{profile}' runs on {BASE_FILE} alone)")
            document = merged(base, load(profile_file))
            documents[where] = (profile, document)
            in_use[profile] |= adapters_in_use(document)

    per_adapter = defaultdict(dict)
    for where, (profile, document) in documents.items():
        for adapter in in_use[profile]:
            per_adapter[adapter][where] = mode_of(document, adapter)

    for adapter, by_place in sorted(per_adapter.items()):
        if len(set(by_place.values())) > 1:
            said = "; ".join(
                f"{where} leaves it at '{mode}'" for where, mode in sorted(by_place.items()))
            errors.append(
                f"adapter '{adapter}': {said}. An application which loads one of these"
                " profiles after the other changes how that BPMS is addressed, so"
                " workflows started before the switch can no longer be found. And a test"
                " which runs another mode than the application tests a configuration the"
                " blueprint does not ship."
            )
    return len(sources), files


def main():

    root = Path(__file__).resolve().parent.parent
    errors = []
    roots = profiles = 0

    for pom in sorted(root.glob("*/*/*/pom.xml")):
        blueprint = pom.parent
        if blueprint.name not in PLATFORMS:
            continue
        read_roots, read_profiles = check_blueprint(blueprint, errors, root)
        roots += read_roots
        profiles += read_profiles

    if errors:
        print("BPMS profiles of one blueprint disagree on how identifiers are scoped:\n",
              file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        print(
            f"\n{len(errors)} problem(s) found. Name the mode in every profile file which"
            " uses that adapter, the test's own included, or in none of them so the default"
            " applies everywhere. A blueprint which really wants to show a change of mode"
            " shows it as a migration, with a second adapter id.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"BPMS profiles agree on scoping: {profiles} profile file(s) in {roots} source"
          " root(s)")


if __name__ == "__main__":
    main()
