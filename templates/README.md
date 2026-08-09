# <Blueprint title>

<!--
Template for the README.md of a blueprint. This file is for HUMANS: it explains the
aspect the blueprint shows and how to run it. Everything a machine has to know goes
into AGENTS.md.

The '##' headings below are checked by bin/check_docs_structure.py - keep them
verbatim, in this order. Remove these comments in a real blueprint.

Never mention the other platform. A Spring Boot blueprint does not know that Quarkus
exists, and the other way round.
-->

<One paragraph: which question this blueprint answers, in the words of somebody who has
that question. No more than four sentences.>

## What this blueprint shows

<The aspect, explained. Name the BPMN elements and the SPI involved and say why it is done
this way - the "why" is the part no reference documentation carries. Do not repeat the
reference documentation, link it in "Documentation" instead.>

## Delta to the base blueprint

<Only what is different from the base blueprint (see the index entry's 'base'), file by
file. A reader who knows the base blueprint should be able to stop reading after this
section.

Omit this section if this blueprint is a base itself.>

## Running it

Requires a JDK 21 and, depending on the BPMS, a running engine (see below).

```bash
mvn verify
mvn -Pcamunda8 verify        # the BPMS is a Maven profile, never a code change
```

Start the application:

```bash
mvn <run command of the platform>
```

Start the process:

```
http://localhost:8080/api/loan-approval/<...>
```

<Then describe the walk-through. At every wait state the application logs one clickable
URL per possible continuation - show an example of that log output here. A browser is the
only tool needed: no curl, no request body.>

## How it works

<The walk-through of the code: which file does what, in the order the process passes them.
Keep it to the files that matter; the boilerplate is listed in AGENTS.md.>

## Documentation

<Links into the reference documentation, each with one line saying what it answers.>

- [spi-for-java](https://github.com/vanillabp/spi-for-java) — using the SPI
- [adapter-platform-integration wiki](https://github.com/vanillabp/adapter-platform-integration/wiki) — concepts, platform integration, configuration
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use — everything specific to that engine

This blueprint is developed in the monorepo
[`blueprints`](https://github.com/vanillabp-blueprints/blueprints) — this repository is a
read-only mirror, **issues and pull requests belong there.**
