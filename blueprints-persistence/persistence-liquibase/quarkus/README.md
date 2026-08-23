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
| `ACT_*`                                                 | the application's Liquibase     | the changelog Camunda ships inside its engine JAR                        |
| `LOAN_APPROVAL`                                         | the workflow module's Liquibase | `loan-approval/.../loan-approval/db/changelog.xml`                       |
| `DATABASECHANGELOG`                                     | Liquibase                       | the bookkeeping, one row per changeset and owner                         |

Three settings are what make this real, and all three are in the configuration rather than
in code: the schema management strategy `validate` has Hibernate check the result instead of
building it, `vanillabp.outbox.create-schema: false` takes VanillaBP's tables out of its own
hands, and `database-schema-update: false` does the same for the engine.

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

That is why one migration run and one bookkeeping table are enough, and it is what VanillaBP's
own changelog does as well. `SchemaIT` asserts both owners are recognizable in that history,
because a module whose changelog forgot its logical path would be recorded under the
application's file name, and nothing else would notice.

It also keeps the application simple. The extension applies one changelog per datasource, so
giving the module a Liquibase of its own would mean a second datasource on the same database,
and an embedded engine would then ask which of the two it belongs in. None of that is needed:
one datasource, one changelog, one include line per module.

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

Which changelog the application applies therefore depends on the engine, and this is the one
engine specific setting which cannot live in the engine's profile file. The extension reads
the changelog while the application is built, so the build fills the name in:

```yaml
quarkus:
  liquibase:
    change-log: db/changelog-@bpms@.xml
```

`db/changelog-camunda7.xml` includes the shared changelog plus Camunda's,
`db/changelog-camunda8.xml` includes the shared one alone, because a remote engine has no
tables here. The engine's own version table, `ACT_GE_SCHEMA_LOG`, stays the engine's business,
and a configured table prefix does not work with Camunda's artifacts, since their statements
carry fixed table names.

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

The phase-two outbox table is checked the same way. To see either message, comment the
include out of `db/changelog.xml` and start the application.

## Delta to the base blueprint

Everything about the process, the aggregate and the wiring is `module-single`. What was added
or changed:

|                            File                            |                                      Change                                       |
|------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `loan-approval/.../loan-approval/db/changelog.xml`         | new: the module's own changelog, its aggregate table                              |
| `application/src/main/resources/db/changelog.xml`          | new: what the application owns, VanillaBP's changelog from the artifact           |
| `application/src/main/resources/db/changelog-camunda7.xml` | new: the same plus the engine's changelog                                         |
| `application/src/main/resources/db/changelog-camunda8.xml` | new: the same alone, since a remote engine has no tables here                     |
| `application/src/main/resources/application.yaml`          | the changelog to apply, `validate`, `create-schema: false`                        |
| `application/src/main/resources/application-camunda7.yaml` | new: `database-schema-update: false` and the datasource the engine runs on        |
| `loan-approval/.../model/Aggregate.java`                   | every column named explicitly, so the entity and the migration cannot drift apart |
| `loan-approval/src/test/resources/application.yaml`        | the module's changelog and `validate`: its test builds its table from it          |
| `application/src/test/.../SchemaIT.java`                   | new: every table is there, one bookkeeping table per owner                        |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | new: a workflow runs through on the migrated schema, across both datasources      |
| both POMs                                                  | `quarkus-liquibase`; the application also `vanillabp-schema`                      |

The entity naming its columns is worth a word: as long as a runtime creates the tables, a
naming strategy decides what they are called, and it is right by definition. Once a migration
creates them, the two have to agree, and a name written down beats a default nobody looked up.

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
mvn -pl application quarkus:dev
```

The log shows the migration running before anything else, one changeset per owner:

```
Running Changeset: vanillabp/schema::vanillabp-phase-two-outbox-2.0.0::VanillaBP
Running Changeset: vanillabp/schema::vanillabp-task-delivery-2.0.0::VanillaBP
Running Changeset: loan-approval::loan-approval-aggregate-1.0.0::blueprint
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

## How it works

The order at startup is what matters here, and the extension takes care of it: the migration
runs while the application starts, before the first bean touches a table, and VanillaBP checks
its tables in a startup observer afterwards. Nothing in this blueprint arranges that order by
hand.

|                            File                            |                                    Role                                     |
|------------------------------------------------------------|-----------------------------------------------------------------------------|
| `application/src/main/resources/application.yaml`          | the changelog to apply, and what is switched off                            |
| `application/src/main/resources/db/changelog.xml`          | includes VanillaBP's changelog                                              |
| `application/src/main/resources/db/changelog-camunda7.xml` | includes the above plus Camunda's own changelog                             |
| `application/src/main/resources/db/changelog-camunda8.xml` | includes the above alone                                                    |
| `loan-approval/.../loan-approval/db/changelog.xml`         | the aggregate table of this workflow module                                 |
| `application/src/test/.../SchemaIT.java`                   | which tables the migration was supposed to bring, and one history per owner |
| `application/src/test/.../WorkflowOnTheOwnSchemaIT.java`   | a process runs through where nothing created a table at runtime             |

Everything else, from `ApiController` through `Service`, `Workflow` and
`WorkflowTaskHandler` to the aggregate, is the base blueprint unchanged.

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
