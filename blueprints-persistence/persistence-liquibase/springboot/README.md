![Header](./readme/vanillabp-headline.png)

# The application owns its database schema

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Once a project is past its first prototype, nothing creates a table at runtime. The schema is a
reviewed, versioned artifact. The deployment pipeline applies it, often with a database user
which the application itself does not even have. This blueprint is `module-single` with that
rule added. Liquibase creates every table, including the ones VanillaBP and the embedded engine
would otherwise create themselves.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The process is the one from `module-single`, and nothing about it changed: a loan approval with
a single service task. What changed is who creates the tables it needs, and who owns which of
them.

|                                                          Table                                                          |           Created by            |                                   From                                   |
|-------------------------------------------------------------------------------------------------------------------------|---------------------------------|--------------------------------------------------------------------------|
| `VANILLABP_PHASE_TWO_OUTBOX`, `VANILLABP_PHASE_TWO_OUTBOX_PAYLOAD`, `VANILLABP_TASK_DELIVERY`, `VANILLABP_HOUSEKEEPING` | the application's Liquibase     | `vanillabp/schema/changelog.xml`, out of `io.vanillabp:vanillabp-schema` |
| `ACT_*`                                                                                                                 | the application's Liquibase     | the changelog Camunda ships inside its engine JAR                        |
| `LOAN_APPROVAL`                                                                                                         | the workflow module's Liquibase | `loan-approval/.../loan-approval/db/changelog.xml`                       |
| `DATABASECHANGELOG`                                                                                                     | Liquibase                       | the bookkeeping, one row per changeset and owner                         |

Three settings make this real, and all three are in the configuration, not in code.
`ddl-auto: validate` makes Hibernate check the result instead of building it.
`vanillabp.outbox.create-schema: false` stops VanillaBP from creating its own tables.
`database-schema-update: false` does the same for the engine.

### Two owners, one history

A workflow module is a JAR which several applications may use, and it owns the tables of its
workflow aggregate. So it brings its changelog along, in its own resource directory. The
application applies it with one line:

```xml
<include file="loan-approval/db/changelog.xml" />
```

Who owns a changeset does not depend on who runs Liquibase. It depends on identity. Liquibase
records a changeset under the `logicalFilePath` its changelog declares, plus its id and author.
It never records it under the file which included it. The module's changelog declares
`logicalFilePath="loan-approval"`. So its rows in `DATABASECHANGELOG` are its own, and a later
version of the module finds its own history:

```
FILENAME          | ID
vanillabp/schema  | vanillabp-task-delivery-2.0.0
loan-approval     | loan-approval-aggregate-1.0.0
```

That is why one Liquibase run and one bookkeeping table are enough. VanillaBP's own changelog
does the same. `SchemaIT` asserts that both owners can be told apart in that history. A module
whose changelog forgot its logical path would be recorded under the application's file name,
and nothing else would notice.

### The tables VanillaBP needs

VanillaBP ships them as a Liquibase changelog in an artifact of its own,
`io.vanillabp:vanillabp-schema`. It contains no class, only the changelog and the SQL generated
from it. So a schema repository can depend on it without pulling in the runtime. The
application includes it from the classpath:

```xml
<include file="vanillabp/schema/changelog.xml" />
```

An update of VanillaBP brings new changesets along in the artifact, and this application's
changelog stays as it is. That works because nobody ever edits a changeset which was applied
somewhere. Liquibase compares checksums and refuses to run when one changed. Getting an
installation out of that state is manual work in somebody's production database. For exactly
that reason VanillaBP keeps each released version in a file of its own and pins it with a
checksum. The changelogs here follow the same rule. Their changeset ids carry the version which
introduced them, and a later change is always a new changeset.

Three tables are described. The phase-two outbox holds what may reach a remote BPMS (business
process management system) only after the caller's transaction committed. A call which carries
a payload stores it in a table of its own, and the entry names the row. When a BPMS repeats a
delivery, VanillaBP answers it from the log of processed task deliveries, and the handler does
not run twice. Each table is described independently of the database. So the statements for a
database nobody tested still come from Liquibase and are not somebody's guess. Tests of the
framework cover H2 and PostgreSQL. MySQL, MariaDB, SQL Server, Oracle and DB2 ship without a
test.

`SchemaIT` does not take that count on trust. It lets Liquibase parse the artifact's changelog,
which follows every include and fills in the table names. It compares the result with the names
written down in the test. So a VanillaBP release which adds a table fails the build here. The
new name then goes into the test, into this README and into `AGENTS.md` in one step. A list
which nobody compares falls behind, and this one did. The payload table of the phase-two outbox
was in the artifact for months, and no test and no document here knew about it.

### The engine's tables

Camunda ships its schema in the engine JAR, as a changelog with a 7.16 baseline plus one
changeset per upgrade. Their `db.name` properties pick the right statements for the database in
use. It is included, never copied, because the engine version on the classpath decides what is
correct:

```xml
<include file="org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml" />
```

So the engine decides which changelog the application applies. The setting is in the engine's
profile file, like every other setting specific to the engine: `db/changelog.xml` alone for a
remote engine, `db/changelog-camunda7.xml` for the embedded one. The engine's own version
table, `ACT_GE_SCHEMA_LOG`, stays the engine's business. A configured table prefix does not
work with either of Camunda's artifacts, because their statements carry fixed table names.

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

Each of the three tables is checked that way, and the message names the one which is missing.
`MissingTableIT` holds this in place: it starts the application with a changelog which forgot
VanillaBP's include line.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring comes from `module-single`. What was
added or changed:

|                            File                            |                                          Change                                          |
|------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/changelog.xml`         | new: the module's own changelog, its aggregate table                                     |
| `application/.../db/changelog.xml`                         | new: what the application applies, including VanillaBP's changelog from the artifact     |
| `application/.../db/changelog-camunda7.xml`                | new: the same plus the engine's changelog, applied by the Camunda 7 build                |
| `application/.../SchemaConfiguration.java`                 | new: the application's Liquibase bean, the changelog named by the engine profile         |
| `application/src/main/resources/application.yaml`          | `ddl-auto: validate`, `create-schema: false`, the changelog to apply                     |
| `application/src/main/resources/application-camunda7.yaml` | `database-schema-update: false` and the changelog including the engine's                 |
| `loan-approval/.../model/Aggregate.java`                   | every column has an explicit name, so the entity and the migration cannot drift apart    |
| `loan-approval/src/test/resources/application.yaml`        | `ddl-auto: validate`: in the module's test its own changelog builds its table            |
| `application/src/test/.../SchemaIT.java`                   | new: the tables the artifact's changelog describes are there, one history for all owners |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | new: a workflow runs through on the migrated schema                                      |
| `application/src/test/.../MissingTableIT.java`             | new: a forgotten migration ends the boot                                                 |
| both POMs                                                  | `spring-boot-liquibase` and `liquibase-core`; the application also `vanillabp-schema`    |

Why the entity names its columns: as long as a runtime creates the tables, a naming strategy
decides their names, and it is right by definition. Once a migration creates them, the two have
to agree. A name written down is better than a default nobody looked up.

Two more details are worth a sentence. `spring-boot-liquibase` is a module of its own since
Spring Boot 4. It makes the entity manager factory wait for the `SpringLiquibase` bean. With
`liquibase-core` alone the migration runs too late or not at all. And a bean of that type makes
Spring Boot's Liquibase auto-configuration step aside. So `spring.liquibase.*` has no effect in
the application. The changelog is wired explicitly, because the engine decides which one it is.
In the module's own test, that auto-configuration is exactly what applies its changelog. That
is why Liquibase is a test dependency there.

## Running it

Requires a JDK 21 or newer. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn verify
mvn -Pcamunda8 verify        # the BPMS is a Maven profile, never a code change
```

Camunda 8 is remote, so a cluster has to run and you have to configure its address, exactly as
in `module-single`.

Start the application:

```bash
mvn -pl application spring-boot:run
```

The log shows the migration running before anything else. The name in front of every changeset
says who owns it:

```
Running Changeset: vanillabp/schema::vanillabp-phase-two-outbox-2.0.0::VanillaBP
Running Changeset: vanillabp/schema::vanillabp-phase-two-payload-2.0.0::VanillaBP
Running Changeset: vanillabp/schema::vanillabp-task-delivery-2.0.0::VanillaBP
Running Changeset: loan-approval::loan-approval-aggregate-1.0.0::blueprint
Running Changeset: org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml::7.16.0-baseline::Camunda
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
configures a user for them. They work on a schema Liquibase built, which is the point of this
blueprint: the engine does not need to have created its tables itself. Where they are served
and how to log in is in the
[adapter's wiki](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/Cockpit-Tasklist-and-Admin).

## How it works

The order at startup is what matters here, and it is not left to chance. Liquibase runs while
the context is being built. Hibernate waits for it, because Spring Boot's Liquibase module
makes the entity manager factory depend on every bean of that type. The engine reaches its
schema through the transaction manager, which is built on top of that same entity manager
factory. VanillaBP checks its tables once all beans exist, in a `SmartInitializingSingleton`.
That is after every migration ran.

|                            File                            |                                                Role                                                 |
|------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `application/.../SchemaConfiguration.java`                 | the application's Liquibase: default bookkeeping tables, changelog per engine                       |
| `application/src/main/resources/db/changelog.xml`          | includes VanillaBP's changelog and the workflow module's                                            |
| `application/src/main/resources/db/changelog-camunda7.xml` | includes the above plus Camunda's own changelog                                                     |
| `loan-approval/.../loan-approval/db/changelog.xml`         | the aggregate table of this workflow module                                                         |
| `application/src/test/.../SchemaIT.java`                   | reads the artifact's changelog to know which tables to expect, and who owns what in the one history |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | a process runs through where nothing created a table at runtime                                     |
| `application/src/test/.../MissingTableIT.java`             | the boot ends when a table is missing, and the message says what to do                              |

Everything else, from `ApiController` through `Service`, `Workflow` and
`WorkflowTaskHandler` to the aggregate, is the base blueprint unchanged.

### Keeping identifiers apart in the BPMS

The BPMS profiles of this blueprint set `name-clash-avoidance: use-prefix`. So VanillaBP puts
the workflow module ID in front of every identifier before it reaches the engine, and takes it
off again on the way back. The BPMN files, the business code and the rest of the configuration
keep the plain names, which is why nothing here mentions the prefix twice. The wiki page
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
