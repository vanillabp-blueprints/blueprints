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
| `ACT_*`                                                 | Flyway, the application's    | Camunda's own scripts, taken out of the engine JAR by the build   |
| `LOAN_APPROVAL`                                         | Flyway, the module's history | `loan-approval/.../loan-approval/db/migration`                    |
| `FLYWAY_SCHEMA_HISTORY*`                                | Flyway                       | one history table per owner, see below                            |

Three settings are what make this real, and all three are in the configuration rather than in
code: the schema management strategy `validate` has Hibernate check the result instead of building
it, `vanillabp.outbox.create-schema: false` takes VanillaBP's tables out of its own hands, and
`database-schema-update: false` does the same for the engine.

### One history, and why

Flyway keeps one timeline per history table, and it knows nothing else about a migration: not who
wrote it, not which artifact it came from. This application applies every owner's migrations in one
run with one history table, and that is a property of the platform rather than a preference:

- the Flyway extension applies ONE configuration per datasource, and a named configuration without a
  datasource of the same name is ignored, even with a `jdbc-url` of its own,
- a migration run of the application's own comes too late, because Hibernate builds its session
  factory - and with `validate` compares it against the schema - before any startup observer runs.

So the locations name one directory per owner and the extension migrates them together:

```yaml
quarkus:
  flyway:
    locations: classpath:vanillabp/schema/flyway/h2,classpath:loan-approval/db/migration,classpath:db/migration,classpath:db/migration-engine
    migrate-at-start: true
```

The price is that the version numbers have to stay apart. VanillaBP numbers its own in 2.x, the
engine uses its version, and what this application and its workflow modules pick is up to them. A
collision is loud rather than silent: Flyway ends the boot with "Found more than one migration with
version X". `SchemaIT` asserts that the migrations of every owner are in that one history, which is
where a location left out of the list would show up.

The blueprint `persistence-liquibase` needs none of this. There a changeset carries the logical path
of the changelog which declared it, so one history distinguishes its owners by itself and the
version numbers of two owners never meet.

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
VanillaBP's own range of version numbers, which is why an application leaves 2.x alone.

A migration which was applied somewhere must never be edited afterwards: Flyway compares checksums
and refuses to run when one changed, and getting an installation out of that state is manual work in
somebody's production database. A later change is a new migration, always.

### The engine's tables, without copying them

Camunda ships its schema as plain scripts in the engine JAR, and they carry no Flyway version and
no name Flyway would accept. Copying them into this repository would be wrong the day the engine
version changes, so the build takes them out of the JAR of exactly the version this application
depends on and names them, in the order Camunda applies them itself:

```
target/classes/db/migration-engine/V7.24.0.1__camunda_engine.sql
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

With a remote engine that directory stays empty, and it exists either way, because Flyway resolves
its locations while the application is built.

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

The phase-two outbox table is checked the same way. To see either message, point
`blueprint.schema.vanillabp-location` at a directory without migrations and start the application.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring is `module-single`. What was added or
changed:

|                             File                             |                                           Change                                           |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/migration/V1.0.0__*.sql` | new: the module's own migration, its aggregate table                                       |
| `application/pom.xml`                                        | the `camunda7` profile takes the engine's scripts out of the engine JAR                    |
| `loan-approval/.../model/Aggregate.java`                     | every column named explicitly, so entity and migration cannot drift apart                  |
| `application/src/main/resources/application.yaml`            | the locations per owner, `validate`, `create-schema: false`, `migrate-at-start: false`     |
| `application/src/main/resources/application-camunda7.yaml`   | `database-schema-update: false` and the engine's migrations                                |
| `loan-approval/src/test/resources/application.yaml`          | `validate` and the module's own migrations, applied by the extension                       |
| `application/src/test/.../SchemaIT.java`                     | new: every table is there, and every owner has a history of its own                        |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`     | new: a workflow runs through on the migrated schema                                        |
| both POMs                                                    | `quarkus-flyway`, in the module for its test only; the application also `vanillabp-schema` |

The entity naming its columns is worth a word: as long as a runtime creates the tables, a naming
strategy decides what they are called, and it is right by definition. Once a migration creates
them, the two have to agree, and a name written down beats a default nobody looked up.

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
mvn -pl application quarkus:dev
```

The log shows the migrations running before anything else, one line per owner's files:

```
Migrating schema "PUBLIC" to version "1.0.0 - loan approval aggregate"
Migrating schema "PUBLIC" to version "2.0.0 - vanillabp schema"
Migrating schema "PUBLIC" to version "7.24.0.1 - camunda engine"
Successfully applied 10 migrations to schema "PUBLIC"
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

## How it works

|                            File                            |                                      Role                                      |
|------------------------------------------------------------|--------------------------------------------------------------------------------|
| `application/src/main/resources/application.yaml`          | which locations belong to which owner, and what is switched off                |
| `application/src/main/resources/application-camunda7.yaml` | the engine's migrations, added where the engine is embedded                    |
| `application/pom.xml`, profile `camunda7`                  | takes Camunda's scripts out of the engine JAR and names them                   |
| `loan-approval/.../loan-approval/db/migration`             | the aggregate table of this workflow module                                    |
| `application/src/test/.../SchemaIT.java`                   | which tables the migration was supposed to bring, and which history holds what |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | a process runs through where nothing created a table at runtime                |

Everything else, from `ApiController` through `Service`, `Workflow` and `WorkflowTaskHandler` to
the aggregate, is the base blueprint unchanged.

### Keeping identifiers apart in the BPMS

The BPMS profiles of this blueprint set `name-clash-avoidance: use-prefix`, so VanillaBP puts
the workflow module ID in front of every identifier before it reaches the engine and takes it
off again on the way back. The BPMN files, the business code and the rest of the configuration
keep the plain names, which is why nothing here mentions the prefix twice. What the modes are
and what each of them costs is explained on the wiki page
[Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided).

## Documentation

- [Creating the tables with Liquibase or Flyway](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#creating-the-tables-with-liquibase-or-flyway): which tables VanillaBP needs, what to apply, which databases are tested
- [The phase-two outbox](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration): what the outbox is for and what it guarantees
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
