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

Checked for every '<group>/<blueprint-id>/<platform>/' and every module of it, one source
root at a time:

  - 'application.yaml' of that source root is the base, and every
    'application-<profile>.yaml' is read on top of it, which is what the platforms do,
  - an adapter is IN USE in a profile when that profile configures it under
    'vanillabp.adapters' or names it in a 'prioritized-adapters' list anywhere,
  - its mode is what the profile names, or 'by-adapter',
  - an adapter which two profiles of one source root use under different modes fails.

Main resources and test resources are compared separately: a test may configure less than
the application, and what has to agree are the profiles one application switches between.

Usage: bin/check_scoping_modes.py
"""

import sys
from collections import defaultdict
from pathlib import Path

import yaml

PLATFORMS = ("springboot", "quarkus")

BASE_FILE = "application.yaml"

PROFILE_FILE = "application-*.yaml"

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


def modes_in_use(document):
    """The mode per adapter this profile leaves in force, as {adapter id: mode}."""

    vanillabp = (document or {}).get("vanillabp")
    if not isinstance(vanillabp, dict):
        return {}
    adapters = vanillabp.get("adapters")
    adapters = adapters if isinstance(adapters, dict) else {}

    in_use = set(adapters) | prioritized_adapters(vanillabp)
    modes = {}
    for adapter in in_use:
        settings = adapters.get(adapter)
        named = settings.get(SETTING) if isinstance(settings, dict) else None
        modes[adapter] = str(named) if named is not None else DEFAULT_MODE
    return modes


def check_source_root(resources, errors, root):
    """Compares the profiles of one source root and returns how many it read."""

    base_file = resources / BASE_FILE
    base = yaml.safe_load(base_file.read_text(encoding="utf-8")) if base_file.exists() else {}

    per_adapter = defaultdict(dict)
    profiles = sorted(resources.glob(PROFILE_FILE))
    for profile in profiles:
        document = merged(base, yaml.safe_load(profile.read_text(encoding="utf-8")))
        for adapter, mode in modes_in_use(document).items():
            per_adapter[adapter][profile.relative_to(root)] = mode

    for adapter, by_profile in sorted(per_adapter.items()):
        if len(set(by_profile.values())) > 1:
            said = ", ".join(
                f"{path} leaves it at '{mode}'" for path, mode in sorted(by_profile.items()))
            errors.append(
                f"adapter '{adapter}': {said}. An application which loads one of these"
                " profiles after the other changes how that BPMS is addressed, so"
                " workflows started before the switch can no longer be found."
            )
    return len(profiles)


def main():

    root = Path(__file__).resolve().parent.parent
    errors = []
    roots = profiles = 0

    for pom in sorted(root.glob("*/*/*/pom.xml")):
        blueprint = pom.parent
        if blueprint.name not in PLATFORMS:
            continue
        for resources in sorted(blueprint.glob("*/src/*/resources")):
            if len(sorted(resources.glob(PROFILE_FILE))) < 2:
                # one profile cannot disagree with itself
                continue
            roots += 1
            profiles += check_source_root(resources, errors, root)

    if errors:
        print("BPMS profiles of one blueprint disagree on how identifiers are scoped:\n",
              file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        print(
            f"\n{len(errors)} problem(s) found. Name the mode in every profile which uses"
            " that adapter, or in none of them so the default applies everywhere. A"
            " blueprint which really wants to show a change of mode shows it as a"
            " migration, with a second adapter id.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"BPMS profiles agree on scoping: {profiles} profile(s) in {roots} source root(s)")


if __name__ == "__main__":
    main()
