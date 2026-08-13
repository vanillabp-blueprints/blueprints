![Header](./readme/vanillabp-headline.png)

# VanillaBP blueprints

Development monorepo of the [VanillaBP](https://www.vanillabp.io) blueprints: many small
projects, each showing and explaining one aspect of building a business process application
with VanillaBP.

[![Build](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/build.yaml/badge.svg)](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/build.yaml)
[![Nightly](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/nightly.yaml/badge.svg)](https://github.com/vanillabp-blueprints/blueprints/actions/workflows/nightly.yaml)
[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

**This README is for contributors.** If you are looking for a blueprint to use, start at the
[organisation page](https://github.com/vanillabp-blueprints). It lists all blueprints by
platform and links the repository of each.

## How blueprints are developed and delivered

Blueprints are *developed* in this monorepo and *delivered* as one repository per blueprint
and platform:

|             |                                                                                          |
|-------------|------------------------------------------------------------------------------------------|
| Development | this repository: one build, one PR for a version bump, one CI run for all blueprints     |
| Delivery    | `vanillabp-blueprints/<blueprint-id>-<platform>`, pushed by CI using `git subtree split` |

The delivered repositories are read-only mirrors. **Issues and pull requests belong here.**

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

Blueprint IDs are `<category>-<aspect>` in lower case, the category being one of `module-`,
`persistence-`, `bpmn-` or `showcase-`. `<platform>` is `springboot` or `quarkus`.

So far the blueprints here are `module-single`, `bpmn-service-task` and `bpmn-user-task`,
all for Spring Boot. They live in a directory per category (`blueprints-modules`,
`blueprints-bpmn`, later `blueprints-persistence` and `showcases`), so the top level stays
readable however many of them there are. The rest of this
repository is the build, the formatting rules and the conventions the others will follow.

## Building

Requires a JDK 21; Maven comes with the wrapper.

```bash
./mvnw install verify                    # all blueprints, default BPMS
./mvnw install verify -Pcamunda8         # ... on another BPMS
./mvnw install verify -pl blueprints-modules/module-single/springboot
./mvnw -N spotless:apply                 # fix formatting violations
```

`install` is needed alongside `verify` because Quarkus integration tests resolve modules from
the local Maven repository.

Each blueprint also builds on its own, which is what a user gets after cloning a delivered
repository:

```bash
cd blueprints-modules/module-single/springboot && mvn verify
```

## The BPMS is a Maven profile

VanillaBP application code is BPMS-invariant, so a blueprint runs on every supported BPMS
without a single line changing. Which one is used is selected by a profile:

`-Pcamunda7` (default) · `-Pcamunda8` · `-Pprocess-engine-api`

## Contributing

Conventions for adding a blueprint, the self-containment rule for blueprint POMs and the
versions currently targeted are described in [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation of VanillaBP itself

Blueprints do not repeat the reference documentation, they link it:

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
