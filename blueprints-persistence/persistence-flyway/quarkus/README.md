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

Three settings make this real, and all three are in the configuration, not in code. The schema
management strategy `validate` makes Hibernate check the result instead of building it.
`vanillabp.outbox.create-schema: false` stops VanillaBP from creating its own tables.
`database-schema-update: false` does the same for the engine.

### One history, and why

Flyway keeps one timeline per history table, and it knows nothing else about a migration: not
who wrote it, not which artifact it came from. This application applies the migrations of every
owner in one run with one history table. The platform forces this, it is not a preference:

- the Flyway extension applies ONE configuration per datasource. A named configuration without
  a datasource of the same name is ignored, even with a `jdbc-url` of its own,
- a migration run of the application's own comes too late. Hibernate builds its session
  factory, and with `validate` compares it with the schema, before any startup observer runs.

So the locations name one directory per owner and the extension migrates them together:

```yaml
quarkus:
  flyway:
    locations: classpath:vanillabp/schema/flyway/h2,classpath:loan-approval/db/migration,classpath:db/migration,classpath:db/migration-engine
    migrate-at-start: true
```

The price is that the version numbers have to stay apart. VanillaBP numbers its own in 2.x, and
the engine uses its version. This application and its workflow modules pick their own. A
collision is loud, not silent: Flyway ends the boot with "Found more than one migration with
version X". `SchemaIT` asserts that the migrations of every owner are in that one history. A
location left out of the list would show up there.

The blueprint `persistence-liquibase` needs none of this. There a changeset carries the logical
path of the changelog which declared it. So one history tells its owners apart by itself, and
the version numbers of two owners never meet.

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
VanillaBP's own range of version numbers. That is why an application leaves 2.x alone.

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
target/classes/db/migration-engine/V7.24.0.1__camunda_engine.sql
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

With a remote engine that directory stays empty. It exists either way, because Flyway resolves
its locations while the application is built.

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

The phase-two outbox table is checked the same way. To see either message, point
`blueprint.schema.vanillabp-location` at a directory without migrations and start the
application.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring comes from `module-single`. What was
added or changed:

|                             File                             |                                           Change                                           |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/migration/V1.0.0__*.sql` | new: the module's own migration, its aggregate table                                       |
| `application/pom.xml`                                        | the `camunda7` profile takes the engine's scripts out of the engine JAR                    |
| `loan-approval/.../model/Aggregate.java`                     | every column has an explicit name, so entity and migration cannot drift apart              |
| `application/src/main/resources/application.yaml`            | the locations per owner, `validate`, `create-schema: false`, `migrate-at-start: false`     |
| `application/src/main/resources/application-camunda7.yaml`   | `database-schema-update: false` and the engine's migrations                                |
| `loan-approval/src/test/resources/application.yaml`          | `validate` and the module's own migrations, applied by the extension                       |
| `application/src/test/.../SchemaIT.java`                     | new: the tables VanillaBP's migrations create are there, one history for all owners        |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`     | new: a workflow runs through on the migrated schema                                        |
| both POMs                                                    | `quarkus-flyway`, in the module for its test only; the application also `vanillabp-schema` |

Why the entity names its columns: as long as a runtime creates the tables, a naming strategy
decides their names, and it is right by definition. Once a migration creates them, the two have
to agree. A name written down is better than a default nobody looked up.

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
property `operaton-engine.version` is the migration version.

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

It answers with the ID of the loan request and logs the URL which shows the result:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

## How it works

|                            File                            |                                           Role                                            |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `application/src/main/resources/application.yaml`          | which locations belong to which owner, and what is switched off                           |
| `application/src/main/resources/application-camunda7.yaml` | the engine's migrations, added where the engine is embedded                               |
| `application/pom.xml`, profile `camunda7`                  | takes Camunda's scripts out of the engine JAR and names them                              |
| `loan-approval/.../loan-approval/db/migration`             | the aggregate table of this workflow module                                               |
| `application/src/test/.../SchemaIT.java`                   | reads VanillaBP's migrations to know which tables to expect, and which history holds what |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | a process runs through where nothing created a table at runtime                           |

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
