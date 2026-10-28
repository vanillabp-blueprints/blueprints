![Header](./readme/vanillabp-headline.png)

# The application owns its database schema, with Flyway

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Once a project is past its first prototype, nothing creates a table at runtime. The schema is a
reviewed, versioned artifact. The deployment pipeline applies it, often with a database user
which the application itself does not even have. This blueprint is `module-single` with that
rule added. Flyway creates every table, including the ones VanillaBP and the embedded engine
would otherwise create themselves.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The process is the one from `module-single`, and nothing about it changed: a loan approval with
a single service task. What changed is who creates the tables it needs, and who owns which of
them.

|                                                          Table                                                          |          Created by          |                               From                                |
|-------------------------------------------------------------------------------------------------------------------------|------------------------------|-------------------------------------------------------------------|
| `VANILLABP_PHASE_TWO_OUTBOX`, `VANILLABP_PHASE_TWO_OUTBOX_PAYLOAD`, `VANILLABP_TASK_DELIVERY`, `VANILLABP_HOUSEKEEPING` | Flyway, VanillaBP's history  | the SQL in `io.vanillabp:vanillabp-schema`, one file per database |
| `ACT_*`                                                                                                                 | Flyway, the application's    | Camunda's own scripts, taken out of the engine JAR by the build   |
| `LOAN_APPROVAL`                                                                                                         | Flyway, the module's history | `loan-approval/.../loan-approval/db/migration`                    |
| `FLYWAY_SCHEMA_HISTORY*`                                                                                                | Flyway                       | one history table per owner, see below                            |

Three settings make this real, and all three are in the configuration, not in code.
`ddl-auto: validate` makes Hibernate check the result instead of building it.
`vanillabp.outbox.create-schema: false` stops VanillaBP from creating its own tables.
`database-schema-update: false` does the same for the engine.

### Three owners, three histories

Flyway keeps one timeline per history table, and it knows nothing else about a migration: not
who wrote it, not which artifact it came from. So two owners in one history compete for version
numbers. VanillaBP ships `V2.0.0`. The day an application picks that number for a migration of
its own, one of the two loses. So every owner gets a history table of its own:

|        Owner        |             History table             |         Migrations         |
|---------------------|---------------------------------------|----------------------------|
| VanillaBP           | `flyway_schema_history_vanillabp`     | out of `vanillabp-schema`  |
| the workflow module | `flyway_schema_history_loan_approval` | out of the module's JAR    |
| the application     | `flyway_schema_history`               | its own, plus the engine's |

The blueprint `persistence-liquibase` does it the other way round, with one history for every
owner. There a changeset carries the logical path of the changelog which declared it. Flyway
has no such identity, so here the table is the border. `SchemaIT` asserts that all three exist
and that each of them counts its own migrations.

Two settings go with that. Without them the second history does not work at all. Flyway refuses
to migrate a schema which already holds tables it has no history for:

```
Found non-empty schema(s) "PUBLIC" but no schema history table. Use baseline() or set
baselineOnMigrate to true to initialize the schema history table.
```

So each instance sets `baselineOnMigrate(true)`, and with it `baselineVersion("0")`. Flyway's
default baseline is version 1. With it, every migration numbered `1.0.0` would be marked as
already applied and skipped without a word.

### The SQL VanillaBP needs

VanillaBP ships it in an artifact of its own, `io.vanillabp:vanillabp-schema`. It is generated
from a Liquibase changelog, so no statement in it is written by hand. It contains no class, so
a schema repository can depend on it without pulling in the runtime. The location names the
database, because Flyway does not hide the differences between SQL dialects:

```yaml
blueprint:
  schema:
    vanillabp-location: classpath:vanillabp/schema/flyway/h2
```

Tests of the framework cover H2 and PostgreSQL. MySQL, MariaDB, SQL Server, Oracle and DB2 ship
without a test. An update of VanillaBP brings its new SQL along in the artifact, numbered in
VanillaBP's own timeline. That is exactly why that timeline has a history table to itself.

`SchemaIT` does not take that on trust. It asks Flyway which migrations it found in VanillaBP's
location. It reads the `CREATE TABLE` statements out of them and compares the result with the
names written down in the test. So a VanillaBP release which adds a table fails the build here.
The new name then goes into the test, into this README and into `AGENTS.md` in one step. A list
which nobody compares falls behind, and this one did. The payload table of the phase-two outbox
was in the artifact for months, and no test and no document here knew about it.

A migration which was applied somewhere must never be edited afterwards. Flyway compares
checksums and refuses to run when one changed. Getting an installation out of that state is
manual work in somebody's production database. A later change is always a new migration.

### The engine's tables, without copying them

Camunda ships its schema as plain scripts in the engine JAR. They carry no Flyway version and
no name Flyway would accept. A copy in this repository would be wrong the day the engine
version changes. So the build takes them out of the JAR of exactly the version this application
depends on. It names them in the order Camunda applies them itself:

```
target/classes/db/migration-camunda7/V7.24.0.1__camunda_engine.sql
                                     V7.24.0.2__camunda_identity.sql
                                     V7.24.0.3__camunda_history.sql
                                     ...
```

Two properties in the `camunda7` profile decide it. `camunda7-engine.version` is also the
migration version. `camunda7-engine.database` is needed because Camunda ships one set of
scripts per database. Raising the engine version raises the schema with it. The new scripts are
new migrations, not edits to applied ones. Those scripts write the engine's own version table,
`ACT_GE_SCHEMA_LOG`, and it stays the engine's business. A configured table prefix does not
work with them, because their statements carry fixed table names.

### The table which is gone

Before release 2.0 the phase-two outbox of this platform was
[gruelbox](https://github.com/gruelbox/transaction-outbox). A blueprint like this one had to
create that library's table, `TXNO_OUTBOX`, as well. Now VanillaBP writes the outbox itself,
into `VANILLABP_PHASE_TWO_OUTBOX` and `VANILLABP_PHASE_TWO_OUTBOX_PAYLOAD`. Both come out of
`vanillabp-schema`, like the delivery table. Nothing here migrates `TXNO_OUTBOX` any more.

An application which wants to keep gruelbox adds the artifact
`io.vanillabp:gruelbox-phase-two-outbox`. That store lives there since it left the platform.
The table then belongs to that library again, and the application migrates it. That is a way
out for an application which already runs gruelbox, not a recipe for a new one. So this
blueprint shows the default.

[Creating the tables with Liquibase or Flyway](https://github.com/vanillabp/adapter-platform-integration/wiki/Spring-Boot-integration#creating-the-tables-with-liquibase-or-flyway)
explains how to hand over the tables, for both migration tools. This blueprint shows it running
instead of explaining it a second time.

### When the migration was not applied

With creation switched off, a forgotten migration used to show up at the first workflow, hours
after the deployment. That is over. VanillaBP checks its tables while the application starts.
If one is missing, it stops the boot and names the table, the property which would have created
it and the artifact to apply.

```
The task-delivery table 'VANILLABP_TASK_DELIVERY' does not exist! VanillaBP remembers
every task delivery it processed in it, so a BPMS repeating a delivery is answered from
it instead of running the handler twice. Either
- apply the schema of VanillaBP with your migration tool: the artifact
  'io.vanillabp:vanillabp-schema' ships the Liquibase changelog
  'vanillabp/schema/changelog.xml' and the SQL generated from it for Flyway, or
- let VanillaBP create the table by setting 'vanillabp.outbox.create-schema' to 'true'
  (the default).
```

Each of the four tables is checked that way, and the message names the one which is missing.
`MissingTableIT` holds this in place: it points VanillaBP's Flyway instance at a location
without migrations.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring comes from `module-single`. What was
added or changed:

|                             File                             |                                                      Change                                                      |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/migration/V1.0.0__*.sql` | new: the module's own migration, its aggregate table                                                             |
| `application/.../SchemaConfiguration.java`                   | new: one Flyway instance and initializer per owner                                                               |
| `application/pom.xml`                                        | the `camunda7` profile takes the engine's scripts out of the engine JAR                                          |
| `loan-approval/.../model/Aggregate.java`                     | every column has an explicit name, so entity and migration cannot drift apart                                    |
| `application/src/main/resources/application.yaml`            | `ddl-auto: validate`, `create-schema: false`, the locations per owner                                            |
| `application/src/main/resources/application-camunda7.yaml`   | `database-schema-update: false` and the engine's migrations                                                      |
| `loan-approval/src/test/resources/application.yaml`          | `ddl-auto: validate` and the module's own migrations                                                             |
| `application/src/test/.../SchemaIT.java`                     | new: the tables VanillaBP's migrations create are there, one history per owner                                   |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`     | new: a workflow runs through on the migrated schema                                                              |
| `application/src/test/.../MissingTableIT.java`               | new: a forgotten migration ends the boot                                                                         |
| both POMs                                                    | `spring-boot-flyway` and `flyway-core`, in the module for its test only; the application also `vanillabp-schema` |

Why the entity names its columns: as long as a runtime creates the tables, a naming strategy
decides their names, and it is right by definition. Once a migration creates them, the two have
to agree. A name written down is better than a default nobody looked up.

`spring-boot-flyway` is a module of its own since Spring Boot 4. The
`FlywayMigrationInitializer` it brings makes the entity manager factory wait for a migration. A
`Flyway` bean alone would migrate whenever its bean happened to be created, and nobody should
rely on that order. When you declare beans of that type, Spring Boot's Flyway
auto-configuration steps aside. So `spring.flyway.*` has no effect in the application. In the
module's own test, that auto-configuration is exactly what applies its migrations.

## Running it

Requires a JDK 21 or newer. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn verify
mvn -Pcamunda8 verify        # the BPMS is a Maven profile, never a code change
```

Camunda 8 is remote, so a cluster has to run and you have to configure its address, exactly as
in `module-single`.

[Operaton](https://operaton.org), the open source successor of Camunda 7, is embedded as well
and needs nothing else either. The BPMN files are the ones of Camunda 7, because Operaton reads
their `camunda:` attributes:

```bash
mvn install verify -Poperaton
```

The `operaton` profile takes the scripts out of Operaton's engine JAR in the same way. Its
property `operaton-engine.version` is the migration version, and the scripts go to
`db/migration-operaton`.

Start the application:

```bash
mvn -pl application spring-boot:run
```

The log shows one migration run per owner before anything else:

```
Successfully applied 1 migration to schema "PUBLIC", now at version v2.0.0     <- VanillaBP
Successfully applied 1 migration to schema "PUBLIC", now at version v1.0.0     <- the module
Successfully applied 7 migrations to schema "PUBLIC", now at version v7.24.0.7 <- the engine
```

Start a loan approval. This is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

It answers with the ID of the loan request and logs the URL which shows the result:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

Camunda 7 serves its own web applications, and the `camunda7` profile of this blueprint
configures a user for them. They work on a schema Flyway built, which is the point of this
blueprint: the engine does not need to have created its tables itself. Where they are served
and how to log in is in the
[adapter's wiki](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/Cockpit-Tasklist-and-Admin).

## How it works

The order at startup is what matters here, and it is not left to chance. Every Flyway instance
has a `FlywayMigrationInitializer` next to it. Spring Boot's Flyway module makes the entity
manager factory depend on beans of that type. The engine reaches its schema through the
transaction manager, which is built on top of that entity manager factory. VanillaBP checks its
own tables once all beans exist, in a `SmartInitializingSingleton`. That is after every
migration ran.

|                            File                            |                                           Role                                            |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `application/.../SchemaConfiguration.java`                 | one Flyway and one initializer per owner, each with its own history                       |
| `application/src/main/resources/application.yaml`          | which locations belong to which owner                                                     |
| `application/src/main/resources/application-camunda7.yaml` | the engine's migrations, added where the engine is embedded                               |
| `application/pom.xml`, profile `camunda7`                  | takes Camunda's scripts out of the engine JAR and names them                              |
| `application/.../db/migration`                             | where the application's own migrations go; empty in this blueprint                        |
| `loan-approval/.../loan-approval/db/migration`             | the aggregate table of this workflow module                                               |
| `application/src/test/.../SchemaIT.java`                   | reads VanillaBP's migrations to know which tables to expect, and which history holds what |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | a process runs through where nothing created a table at runtime                           |
| `application/src/test/.../MissingTableIT.java`             | the boot ends when a table is missing, and the message says what to do                    |

Everything else, from `ApiController` through `Service`, `Workflow` and `WorkflowTaskHandler` to
the aggregate, is the base blueprint unchanged.

### Keeping identifiers apart in the BPMS

The BPMS (business process management system) profiles of this blueprint set
`name-clash-avoidance: use-prefix`. So VanillaBP puts the workflow module ID in front of every
identifier before it reaches the engine, and takes it off again on the way back. The BPMN
files, the business code and the rest of the configuration keep the plain names, which is why
nothing here mentions the prefix twice. The wiki page
[Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided)
explains the modes and what each of them costs.

## Documentation

- [Creating the tables with Liquibase or Flyway](https://github.com/vanillabp/adapter-platform-integration/wiki/Spring-Boot-integration#creating-the-tables-with-liquibase-or-flyway): which tables VanillaBP needs, what to apply, which databases are tested
- [The phase-two outbox](https://github.com/vanillabp/adapter-platform-integration/wiki/Spring-Boot-integration): what the outbox is for and what it guarantees
- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is and what belongs to it
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: whether the engine has a schema of its own, and how to hand it over

This blueprint is developed in the monorepo
[`blueprints`](https://github.com/vanillabp-blueprints/blueprints). This repository is a
read-only mirror, **issues and pull requests belong there.**

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by [Phactum](https://www.phactum.at) with the
intention of giving back to the community as it has benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2026 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0
