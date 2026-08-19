![Header](./readme/vanillabp-headline.png)

# The application owns its database schema

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

In a project past its first prototype nothing creates a table at runtime. The schema is a
reviewed, versioned artifact, applied by the deployment pipeline, often by a database user
the application itself does not even have. This blueprint is `module-single` with that
constraint added: Liquibase creates every table, including the ones VanillaBP and the
embedded engine would otherwise create themselves.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The process is the one from `module-single` and nothing about it changed: a loan approval
with a single service task. What changed is who creates the tables it needs, and who owns
which of them.

|                          Table                          |           Created by            |                                   From                                   |
|---------------------------------------------------------|---------------------------------|--------------------------------------------------------------------------|
| `VANILLABP_PHASE_TWO_OUTBOX`, `VANILLABP_TASK_DELIVERY` | the application's Liquibase     | `vanillabp/schema/changelog.xml`, out of `io.vanillabp:vanillabp-schema` |
| `TXNO_OUTBOX`, `TXNO_SEQUENCE`                          | the application's Liquibase     | `application/.../db/gruelbox-outbox.xml`                                 |
| `ACT_*`                                                 | the application's Liquibase     | the changelog Camunda ships inside its engine JAR                        |
| `LOAN_APPROVAL`                                         | the workflow module's Liquibase | `loan-approval/.../loan-approval/db/changelog.xml`                       |
| `DATABASECHANGELOG`                                     | Liquibase                       | the bookkeeping, one row per changeset and owner                         |

Three settings are what make this real, and all three are in the configuration rather than
in code: `ddl-auto: validate` has Hibernate check the result instead of building it,
`vanillabp.outbox.create-schema: false` takes VanillaBP's tables out of its own hands, and
`database-schema-update: false` does the same for the engine.

### Two owners, one history

A workflow module is a JAR which several applications may use, and it owns the tables of its
workflow aggregate. So it brings its changelog along, in its own resource directory, and the
application applies it with one line:

```xml
<include file="loan-approval/db/changelog.xml" />
```

Ownership is not a matter of who runs Liquibase, it is a matter of identity. Liquibase records
a changeset under the `logicalFilePath` its changelog declares, plus its id and author, never
under the file which included it. The module's changelog declares `logicalFilePath="loan-approval"`,
so its rows in `DATABASECHANGELOG` are its own and a later version of the module finds its own
history:

```
FILENAME          | ID
vanillabp/schema  | vanillabp-task-delivery-2.0.0
loan-approval     | loan-approval-aggregate-1.0.0
```

That is why one Liquibase run and one bookkeeping table are enough, and it is what VanillaBP's
own changelog does as well. `SchemaIT` asserts both owners are recognizable in that history,
because a module whose changelog forgot its logical path would be recorded under the
application's file name, and nothing else would notice.

### The tables VanillaBP needs

VanillaBP ships them as a Liquibase changelog in an artifact of its own,
`io.vanillabp:vanillabp-schema`. It contains no class, only the changelog and the SQL
generated from it, so a schema repository can depend on it without pulling the runtime. The
application includes it from the classpath:

```xml
<include file="vanillabp/schema/changelog.xml" />
```

An update of VanillaBP brings new changesets along in the artifact and this application's
changelog stays as it is. That works because a changeset which was applied somewhere is
never edited: Liquibase compares checksums and refuses to run when one changed, and getting
an installation out of that state is manual work in somebody's production database. VanillaBP
keeps each released version in a file of its own and pins it with a checksum for exactly that
reason, and the changelogs here follow the same rule. Their changeset ids carry the version
which introduced them, and a later change is a new changeset, always.

Two tables are described: the phase-two outbox, which holds what may only reach a remote BPMS
after the caller's transaction committed, and the log of processed task deliveries, from which
a BPMS repeating a delivery is answered instead of running the handler twice. Both are
described database independently, so the statements for a database nobody tested are still
Liquibase's own rather than somebody's guess. H2 and PostgreSQL are covered by tests of the
framework; MySQL, MariaDB, SQL Server, Oracle and DB2 are shipped without one.

### The engine's tables

Camunda ships its schema in the engine JAR, as a changelog with a 7.16 baseline plus one
changeset per upgrade, whose `db.name` properties pick the right statements for the database
in use. It is included, never copied, because the engine version on the classpath decides
what is correct:

```xml
<include file="org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml" />
```

Which changelog the application applies is therefore a matter of the engine, and lives in the
engine's profile file like every other engine specific setting: `db/changelog.xml` on its own
for a remote engine, `db/changelog-camunda7.xml` for the embedded one. The engine's own
version table, `ACT_GE_SCHEMA_LOG`, stays the engine's business. A configured table prefix
does not work with either of Camunda's artifacts, since their statements carry fixed table
names.

### Somebody else's schema: the outbox table

This is the one place where this application writes down a schema which is not its own. On
this platform VanillaBP's phase-two outbox is [gruelbox](https://github.com/gruelbox/transaction-outbox),
which brings its own migrator, and VanillaBP deliberately ships no statements for
`TXNO_OUTBOX`. But `vanillabp.outbox.create-schema` covers both: switching it off so that
Liquibase can own VanillaBP's tables switches gruelbox's migrator off as well, so the
application has to create that table too.

Where the statements in `db/gruelbox-outbox.xml` come from matters: gruelbox's migrator was
let loose on an empty database once and the result was read back out of it. They were not
written by hand from gruelbox's source, where the schema is thirteen migrations, some in
MySQL syntax, several overridden per dialect. `GruelboxSchemaDriftTest` repeats that
migration on every build and compares, so a gruelbox version which adds or renames a column
fails a build instead of a deployment.

### When the migration was not applied

With creation switched off, a forgotten migration used to surface at the first workflow,
hours after the deployment. It does not any more: VanillaBP checks its tables while the
application starts and ends the boot with the table, the property which would have created
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

`MissingTableIT` pins it by starting the application with a changelog which forgot VanillaBP's include line. There is one
gap to know about: the outbox table of the outbox library is not checked this way, so a
missing `TXNO_OUTBOX` still shows up at the first workflow which is started.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring is `module-single`. What was added
or changed:

|                              File                               |                                        Change                                         |
|-----------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/changelog.xml`              | new: the module's own changelog, its aggregate table                                  |
| `loan-approval/.../config/LoanApprovalSchemaConfiguration.java` | new: the module's Liquibase bean and its own bookkeeping tables                       |
| `application/.../db/changelog.xml`                              | new: what the application owns, including VanillaBP's changelog from the artifact     |
| `application/.../db/changelog-camunda7.xml`                     | new: the same plus the engine's changelog, applied by the Camunda 7 build             |
| `application/.../db/gruelbox-outbox.xml`                        | new: the outbox table of the outbox library                                           |
| `application/.../SchemaConfiguration.java`                      | new: the application's Liquibase bean, the changelog named by the engine profile      |
| `application/src/main/resources/application.yaml`               | `ddl-auto: validate`, `create-schema: false`, the changelog to apply                  |
| `application/src/main/resources/application-camunda7.yaml`      | `database-schema-update: false` and the changelog including the engine's              |
| `loan-approval/.../model/Aggregate.java`                        | every column named explicitly, so the entity and the migration cannot drift apart     |
| `loan-approval/src/test/resources/application.yaml`             | `ddl-auto: validate`: in the module's test its own changelog builds its table         |
| `application/src/test/.../SchemaIT.java`                        | new: every table is there, one bookkeeping table per owner                            |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`        | new: a workflow runs through on the migrated schema                                   |
| `application/src/test/.../MissingTableIT.java`                  | new: a forgotten migration ends the boot                                              |
| `application/src/test/.../GruelboxSchemaDriftTest.java`         | new: the copied statements still match what the library's migrator creates            |
| both POMs                                                       | `spring-boot-liquibase` and `liquibase-core`; the application also `vanillabp-schema` |

The entity naming its columns is worth a word: as long as a runtime creates the tables, a
naming strategy decides what they are called, and it is right by definition. Once a migration
creates them, the two have to agree, and a name written down beats a default nobody looked up.

Two more details are worth a sentence. `spring-boot-liquibase` is a module of its own since
Spring Boot 4, and it is what makes the entity manager factory wait for the `SpringLiquibase`
bean, so `liquibase-core` alone leaves the migration running too late or not at all. And a
bean of that type makes Spring Boot's Liquibase auto-configuration step aside, so
`spring.liquibase.*` has no effect in the application: the changelog is wired explicitly,
because the engine decides which one it is. In the module's own test that auto-configuration
is exactly what applies its changelog, which is why Liquibase is a test dependency there.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn verify
mvn -Pcamunda8 verify        # the BPMS is a Maven profile, never a code change
```

Camunda 8 is remote, so a cluster has to run and its address has to be configured, exactly as
in `module-single`.

Start the application:

```bash
mvn -pl application spring-boot:run
```

The log shows the migration running before anything else, one changeset per owner:

```
Running Changeset: vanillabp/schema::vanillabp-phase-two-outbox-2.0.0::VanillaBP
Running Changeset: vanillabp/schema::vanillabp-task-delivery-2.0.0::VanillaBP
Running Changeset: loan-approval::loan-approval-aggregate-1.0.0::blueprint
Running Changeset: db/gruelbox-outbox.xml::gruelbox-outbox-1.0.0::blueprint
Running Changeset: org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml::7.16.0-baseline::Camunda
```

Start a loan approval. This is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

It answers with the ID of the loan request and logs the URL showing the result:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

While the application runs on Camunda 7, Camunda's web applications are at
`http://localhost:8080/camunda`, user `demo`, password `demo`. They work on a schema
Liquibase built, which is the point of this blueprint: the engine does not need to have
created its tables itself.

## How it works

The order at startup is what matters here, and it is not left to chance. Liquibase runs while
the context is being built, and Hibernate waits for it because Spring Boot's Liquibase module
lets the entity manager factory depend on every bean of that type. The engine reaches its schema through the transaction manager, which is built
on top of that same entity manager factory. VanillaBP checks its tables once all beans exist,
in a `SmartInitializingSingleton`, which is after every migration ran.

|                              File                               |                                     Role                                      |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------|
| `application/.../SchemaConfiguration.java`                      | the application's Liquibase: default bookkeeping tables, changelog per engine |
| `application/src/main/resources/db/changelog.xml`               | includes VanillaBP's changelog and the outbox library's table                 |
| `application/src/main/resources/db/changelog-camunda7.xml`      | includes the above plus Camunda's own changelog                               |
| `application/src/main/resources/db/gruelbox-outbox.xml`         | the outbox table, as the library's migrator would have created it             |
| `loan-approval/.../config/LoanApprovalSchemaConfiguration.java` | the module's Liquibase: its own changelog, its own bookkeeping tables         |
| `loan-approval/.../loan-approval/db/changelog.xml`              | the aggregate table of this workflow module                                   |
| `application/src/test/.../SchemaIT.java`                        | which tables the migration was supposed to bring, and one history per owner   |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`        | a process runs through where nothing created a table at runtime               |
| `application/src/test/.../MissingTableIT.java`                  | the boot ends when a table is missing, and the message says what to do        |
| `application/src/test/.../GruelboxSchemaDriftTest.java`         | the copied statements are compared against the library's migrator             |

Everything else, from `ApiController` through `Service`, `Workflow` and
`WorkflowTaskHandler` to the aggregate, is the base blueprint unchanged.

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
