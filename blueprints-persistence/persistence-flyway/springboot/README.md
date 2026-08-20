![Header](./readme/vanillabp-headline.png)

# The application owns its database schema, with Flyway

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

In a project past its first prototype nothing creates a table at runtime. The schema is a
reviewed, versioned artifact, applied by the deployment pipeline, often by a database user the
application itself does not even have. This blueprint is `module-single` with that constraint
added: Flyway creates every table, including the ones VanillaBP and the embedded engine would
otherwise create themselves.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The process is the one from `module-single` and nothing about it changed: a loan approval with a
single service task. What changed is who creates the tables it needs, and who owns which of them.

|                          Table                          |          Created by          |                               From                                |
|---------------------------------------------------------|------------------------------|-------------------------------------------------------------------|
| `VANILLABP_PHASE_TWO_OUTBOX`, `VANILLABP_TASK_DELIVERY` | Flyway, VanillaBP's history  | the SQL in `io.vanillabp:vanillabp-schema`, one file per database |
| `TXNO_OUTBOX`, `TXNO_SEQUENCE`                          | Flyway, the application's    | `application/.../db/migration`                                    |
| `ACT_*`                                                 | Flyway, the application's    | Camunda's own scripts, taken out of the engine JAR by the build   |
| `LOAN_APPROVAL`                                         | Flyway, the module's history | `loan-approval/.../loan-approval/db/migration`                    |
| `FLYWAY_SCHEMA_HISTORY*`                                | Flyway                       | one history table per owner, see below                            |

Three settings are what make this real, and all three are in the configuration rather than in
code: `ddl-auto: validate` has Hibernate check the result instead of building it,
`vanillabp.outbox.create-schema: false` takes VanillaBP's tables out of its own hands, and
`database-schema-update: false` does the same for the engine.

### Three owners, three histories

Flyway keeps one timeline per history table, and it knows nothing else about a migration: not who
wrote it, not which artifact it came from. Two owners in one history therefore compete for version
numbers. VanillaBP ships `V2.0.0`; the day an application picks that number for a migration of its
own, one of the two loses. So every owner gets a history table of its own:

|        Owner        |             History table             |         Migrations         |
|---------------------|---------------------------------------|----------------------------|
| VanillaBP           | `flyway_schema_history_vanillabp`     | out of `vanillabp-schema`  |
| the workflow module | `flyway_schema_history_loan_approval` | out of the module's JAR    |
| the application     | `flyway_schema_history`               | its own, plus the engine's |

The blueprint `persistence-liquibase` does it the other way round, with one history for every
owner, because there a changeset carries the logical path of the changelog which declared it.
Flyway has no such identity, so here the table is the boundary. `SchemaIT` asserts all three exist and that each of them counts
its own migrations.

Two settings go with that, and without them the second history does not work at all. Flyway refuses
to migrate a schema which already holds tables it has no history for:

```
Found non-empty schema(s) "PUBLIC" but no schema history table. Use baseline() or set
baselineOnMigrate to true to initialize the schema history table.
```

So each instance sets `baselineOnMigrate(true)`, and with it `baselineVersion("0")`, because
Flyway's default baseline is version 1 and every migration numbered `1.0.0` would be marked as
already applied and silently skipped.

### The SQL VanillaBP needs

VanillaBP ships it in an artifact of its own, `io.vanillabp:vanillabp-schema`, generated from a
Liquibase changelog so that no hand-written statement is in it. It contains no class, so a schema
repository can depend on it without pulling the runtime. The location names the database, because
Flyway has no abstraction over dialects:

```yaml
blueprint:
  schema:
    vanillabp-location: classpath:vanillabp/schema/flyway/h2
```

H2 and PostgreSQL are covered by tests of the framework; MySQL, MariaDB, SQL Server, Oracle and
DB2 ship without one. An update of VanillaBP brings its new SQL along in the artifact, numbered in
VanillaBP's own timeline, which is exactly why that timeline has a history table to itself.

A migration which was applied somewhere must never be edited afterwards: Flyway compares
checksums and refuses to run when one changed, and getting an installation out of that state is
manual work in somebody's production database. A later change is a new migration, always.

### The engine's tables, without copying them

Camunda ships its schema as plain scripts in the engine JAR, and they carry no Flyway version and
no name Flyway would accept. Copying them into this repository would be wrong the day the engine
version changes, so the build takes them out of the JAR of exactly the version this application
depends on and names them, in the order Camunda applies them itself:

```
target/classes/db/migration-camunda7/V7.24.0.1__camunda_engine.sql
                                     V7.24.0.2__camunda_identity.sql
                                     V7.24.0.3__camunda_history.sql
                                     ...
```

Two properties in the `camunda7` profile decide it: `camunda7-engine.version`, which is also the
migration version, and `camunda7-engine.database`, because Camunda ships one set of scripts per
database. Raising the engine version raises the schema with it, and the new scripts are new
migrations rather than edits to applied ones. The engine's own version table,
`ACT_GE_SCHEMA_LOG`, is written by those scripts and stays the engine's business, and a configured
table prefix does not work with them, since their statements carry fixed table names.

### Somebody else's schema: the outbox table

This is the one place where this application writes down a schema which is not its own. On this
platform VanillaBP's phase-two outbox is [gruelbox](https://github.com/gruelbox/transaction-outbox),
which brings its own migrator, and VanillaBP deliberately ships no statements for `TXNO_OUTBOX`.
But `vanillabp.outbox.create-schema` covers both: switching it off so that Flyway can own
VanillaBP's tables switches that migrator off as well, so the application has to create the table
too.

Where the statements come from matters, and the answer is that nobody wrote them: gruelbox writes
them itself.

```java
DefaultPersistor.builder().dialect(Dialect.H2).build().writeSchema(writer);
```

That emits every migration of the library as SQL for the dialect it is given, which is what
`V1.0.0__outbox_of_the_outbox_library.sql` carries, in gruelbox's own order and with its own
numbering. `GruelboxSchemaDriftTest` asks for the same output on every build and compares the
statements, so a version of the library which adds or changes a migration fails a build instead of a
deployment. No database is started for that comparison, and nothing has to be read out of a migrated
one.

`TXNO_VERSION` is not created here. It is how the migrator remembers where it got to, and the
migrator is off. `writeSchema` does not emit it either.

What a handover of the tables involves, on both platforms and for both migration tools, is documented
in
[Creating the tables with Liquibase or Flyway](https://github.com/vanillabp/adapter-platform-integration/wiki/Spring-Boot-integration#creating-the-tables-with-liquibase-or-flyway).
This blueprint shows it running rather than explaining it a second time.

### When the migration was not applied

With creation switched off, a forgotten migration used to surface at the first workflow, hours
after the deployment. It does not any more: VanillaBP checks its tables while the application
starts and ends the boot with the table, the property which would have created it and the artifact
to apply.

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

`TXNO_OUTBOX` is checked the same way, and its message says what the table is: gruelbox's own, not
part of `vanillabp-schema`, and its statements come from `writeSchema`.

```
The phase-two outbox table 'TXNO_OUTBOX' does not exist! Starting a workflow on a remote
BPMS writes an entry into it inside the caller's transaction, so without the table nothing
can be started. This table is gruelbox's own, not VanillaBP's: it is NOT part of
'io.vanillabp:vanillabp-schema' and gruelbox's schema migration is switched off here.
```

`MissingTableIT` pins both, by pointing one Flyway instance at a location without migrations:
VanillaBP's in the first case, the application's own in the second, which is the one carrying
gruelbox's migration.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring is `module-single`. What was added or
changed:

|                             File                             |                                                      Change                                                      |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/migration/V1.0.0__*.sql` | new: the module's own migration, its aggregate table                                                             |
| `application/.../db/migration/V1.0.0__*.sql`                 | new: the table of the outbox library                                                                             |
| `application/.../SchemaConfiguration.java`                   | new: one Flyway instance and initializer per owner                                                               |
| `application/pom.xml`                                        | the `camunda7` profile takes the engine's scripts out of the engine JAR                                          |
| `loan-approval/.../model/Aggregate.java`                     | every column named explicitly, so entity and migration cannot drift apart                                        |
| `application/src/main/resources/application.yaml`            | `ddl-auto: validate`, `create-schema: false`, the locations per owner                                            |
| `application/src/main/resources/application-camunda7.yaml`   | `database-schema-update: false` and the engine's migrations                                                      |
| `loan-approval/src/test/resources/application.yaml`          | `ddl-auto: validate` and the module's own migrations                                                             |
| `application/src/test/.../SchemaIT.java`                     | new: every table is there, and every owner has a history of its own                                              |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`     | new: a workflow runs through on the migrated schema                                                              |
| `application/src/test/.../MissingTableIT.java`               | new: a forgotten migration ends the boot                                                                         |
| `application/src/test/.../GruelboxSchemaDriftTest.java`      | new: the copied statements still match what the library's migrator creates                                       |
| both POMs                                                    | `spring-boot-flyway` and `flyway-core`, in the module for its test only; the application also `vanillabp-schema` |

The entity naming its columns is worth a word: as long as a runtime creates the tables, a naming
strategy decides what they are called, and it is right by definition. Once a migration creates
them, the two have to agree, and a name written down beats a default nobody looked up.

`spring-boot-flyway` is a module of its own since Spring Boot 4, and the `FlywayMigrationInitializer`
it brings is what makes the entity manager factory wait for a migration. A `Flyway` bean alone
would migrate whenever its bean happened to be created, which is not an order anybody should rely
on. Declaring beans of that type also makes Spring Boot's Flyway auto-configuration step aside, so
`spring.flyway.*` has no effect in the application. In the module's own test that
auto-configuration is exactly what applies its migrations.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn verify
mvn -Pcamunda8 verify        # the BPMS is a Maven profile, never a code change
```

Camunda 8 is remote, so a cluster has to run and its address has to be configured, exactly as in
`module-single`.

Start the application:

```bash
mvn -pl application spring-boot:run
```

The log shows one migration run per owner before anything else:

```
Successfully applied 2 migrations to schema "PUBLIC" (execution time 00:00.031s)
Successfully applied 1 migration to schema "PUBLIC" (execution time 00:00.008s)
Successfully applied 8 migrations to schema "PUBLIC" (execution time 00:00.402s)
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
`http://localhost:8080/camunda`, user `demo`, password `demo`. They work on a schema Flyway built,
which is the point of this blueprint: the engine does not need to have created its tables itself.

## How it works

The order at startup is what matters here, and it is not left to chance. Every Flyway instance is
paired with a `FlywayMigrationInitializer`, and Spring Boot's Flyway module lets the entity manager
factory depend on beans of that type. The engine reaches its schema through the transaction
manager, which is built on top of that entity manager factory. VanillaBP checks its own tables once
all beans exist, in a `SmartInitializingSingleton`, which is after every migration ran.

|                            File                            |                                      Role                                      |
|------------------------------------------------------------|--------------------------------------------------------------------------------|
| `application/.../SchemaConfiguration.java`                 | one Flyway and one initializer per owner, each with its own history            |
| `application/src/main/resources/application.yaml`          | which locations belong to which owner                                          |
| `application/src/main/resources/application-camunda7.yaml` | the engine's migrations, added where the engine is embedded                    |
| `application/pom.xml`, profile `camunda7`                  | takes Camunda's scripts out of the engine JAR and names them                   |
| `application/.../db/migration`                             | the table of the outbox library                                                |
| `loan-approval/.../loan-approval/db/migration`             | the aggregate table of this workflow module                                    |
| `application/src/test/.../SchemaIT.java`                   | which tables the migration was supposed to bring, and which history holds what |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | a process runs through where nothing created a table at runtime                |
| `application/src/test/.../MissingTableIT.java`             | the boot ends when a table is missing, and the message says what to do         |
| `application/src/test/.../GruelboxSchemaDriftTest.java`    | the copied statements are compared against the library's migrator              |

Everything else, from `ApiController` through `Service`, `Workflow` and `WorkflowTaskHandler` to
the aggregate, is the base blueprint unchanged.

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
