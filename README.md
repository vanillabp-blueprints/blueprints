![Header](./readme/vanillabp-headline.png)

# VanillaBP blueprints

The development monorepo of the [VanillaBP](https://www.vanillabp.io) blueprints. A monorepo is
one repository which holds many projects. Here these are many small projects. Each one shows
and explains one aspect of building a business process application with VanillaBP.

[![Build](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/build.yaml/badge.svg)](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/build.yaml)
[![Nightly build](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/nightly.yaml/badge.svg)](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/nightly.yaml)
[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

**This README is for contributors.** If you look for a blueprint to use, start at the
[organisation page](https://github.com/vanillabp-blueprints). It lists all blueprints by
platform and links the repository of each.

## How blueprints are developed and delivered

Blueprints are *developed* in this monorepo. They are *delivered* as one repository per
blueprint and platform:

|             |                                                                                          |
|-------------|------------------------------------------------------------------------------------------|
| Development | this repository: one build, one PR for a version bump, one CI run for all blueprints     |
| Delivery    | `vanillabp-blueprints/<blueprint-id>-<platform>`, pushed by CI using `git subtree split` |

The delivered repositories are read-only copies, called mirrors. **Issues and pull requests
belong here.**

## Repository layout

```
blueprints/
├── pom.xml                  aggregator, formatting, BPMS profiles
├── formatting_conventions.xml
├── <group>/                 one per category: blueprints-modules, blueprints-persistence,
│   │                        blueprints-bpmn, showcases
│   └── <blueprint-id>/
│       ├── springboot/      -> repository <blueprint-id>-springboot
│       ├── quarkus/         -> repository <blueprint-id>-quarkus
│       └── twin-diff-allow.txt  approved differences between the platform twins
...
```

Blueprint IDs are `<category>-<aspect>` in lower case. The category is one of `module-`,
`persistence-`, `bpmn-` or `showcase-`. `<platform>` is `springboot` or `quarkus`.

So far the blueprints here are the `module-*`, `persistence-*` and `bpmn-*` families, each
blueprint for both platforms, except `persistence-active-record`, which is for Quarkus alone.
Its subject is something Spring Boot does not have. The index records that as `not-applicable`
with a reason, not as a gap. The blueprints live in a directory
per category (`blueprints-modules`, `blueprints-persistence`, `blueprints-bpmn`, later
`showcases`). So the top level stays readable, however many of them there are. The rest of this
repository is the build, the formatting rules and the conventions the others follow.

## Building

Requires a JDK 21 or newer. Maven comes with the wrapper. The workflows build with the JDK
named in `.github/workflows`, currently 25. Every blueprint compiles for Java 21 either way, so
a copy of it runs on a Java 21 runtime.

```bash
./mvnw install verify                    # all blueprints, on the default BPMS
./mvnw install verify -P<bpms>           # ... on another one
./mvnw install verify -pl blueprints-modules/module-single/springboot
./mvnw -N spotless:apply                 # fix formatting violations
```

`install` is needed next to `verify`, because Quarkus integration tests resolve modules from
the local Maven repository.

Each blueprint also builds on its own. That is what a user gets after cloning a delivered
repository:

```bash
cd blueprints-modules/module-single/springboot && mvn verify
```

## The BPMS is a Maven profile

VanillaBP application code is the same for every BPMS (business process management system). So
a blueprint runs on every BPMS it declares, and not a single line changes. A Maven profile
named on the command line decides which one a build uses. One of the profiles is active by
default.

Each blueprint declares its profiles in its own POM, so that POM is the list. The `bpms` entry
of the blueprint in `blueprints.yaml` names the same set. CI builds against that entry.

What a BPMS does behind its profile is not explained here. The wiki of its adapter explains it,
and `Documentation of VanillaBP itself` below links it. A blueprint which needed that knowledge
would prove the opposite of what a blueprint is for.

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) describes the conventions for adding a blueprint, the rule
that a blueprint POM builds on its own, and the versions currently targeted.

## Documentation of VanillaBP itself

Blueprints do not repeat the reference documentation. They link it:

- [spi-for-java](https://github.com/vanillabp/spi-for-java): using the SPI
- [adapter-platform-integration wiki](https://github.com/vanillabp/adapter-platform-integration/wiki): concepts, platforms, configuration
- the wiki of the respective [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters): BPMS-specific fine-tuning

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by [Phactum](https://www.phactum.at) with the
intention of giving back to the community as it has benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2026 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0
