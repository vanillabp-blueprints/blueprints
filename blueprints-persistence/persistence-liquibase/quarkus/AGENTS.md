# persistence-liquibase

`module-single` with the database schema taken out of the runtime's hands: Liquibase creates
every table, including the ones VanillaBP and an embedded engine would create themselves. Two
owners bring changelogs, the workflow module and the application, and one migration run applies
them; who owns which changeset is decided by the `logicalFilePath` its changelog declares.

Build `module-single` first and apply this delta; nothing about the process, the aggregate or
the BPMN wiring differs.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It carries the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |
| `LOAN_APPROVAL`            | the aggregate's table, in the entity AND in the module's changelog                                                        |

Two names are not placeholders and must not be renamed: `VANILLABP_PHASE_TWO_OUTBOX` and
`VANILLABP_TASK_DELIVERY` are VanillaBP's tables. The delivery table's name is not
configurable at all, so a renamed one is a table nobody reads.

`loan-approval` is also the `logicalFilePath` of the module's changelog. Renaming the module
means renaming that path, and a changelog already applied somewhere must not have its path
changed: Liquibase would no longer recognize its rows and would run every changeset again.

## Core files

|                               File                                |                                                    Why it matters                                                     |
|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/db/changelog.xml` | the module's schema: its aggregate table. Inside the module's resource directory, because modules share one classpath |
| `application/src/main/resources/db/changelog.xml`                 | what the application owns: `<include>` of `vanillabp/schema/changelog.xml` from the artifact                          |
| `application/src/main/resources/db/changelog-camunda7.xml`        | the same plus `<include>` of Camunda's changelog from the engine JAR                                                  |
| `application/src/main/resources/db/changelog-camunda8.xml`        | the same alone: a remote engine has no tables here                                                                    |
| `application/src/main/resources/application.yaml`                 | the changelog to apply, `strategy: validate`, `vanillabp.outbox.create-schema: false`                                 |
| `application/src/main/resources/application-camunda7.yaml`        | `database-schema-update: false`, so the embedded engine leaves its tables to Liquibase                                |
| `loan-approval/src/test/resources/application.yaml`               | the module's own changelog and `validate`: its test is an application which applies it                                |
| `application/src/test/java/.../SchemaIT.java`                     | asserts every table exists and that there is one bookkeeping table per owner                                          |
| `application/src/test/java/.../WorkflowOnTheOwnSchemaIT.java`     | runs a workflow in the application, which is the only place where two datasources and a migrated schema meet          |

Rules which hold beyond this blueprint:

- A changeset which was applied somewhere is never edited. Liquibase compares checksums and
  refuses to run, and repairing that means manual work in a production database. Add a new
  changeset instead, with the version which introduces it in its id.
- Never copy VanillaBP's or the engine's statements into the application. Both ship them, and
  both decide by their version what is correct. `<include>` reaches into a JAR on the
  classpath.
- Ownership is identity, not a table. A changelog declares its own `logicalFilePath`, and
  Liquibase records its changesets under that path rather than under the file which included
  them. One history therefore holds every owner, and a module stays upgradable on its own. A
  changelog without that attribute is recorded under whatever file included it, which is the
  mistake this rule exists for. It is also what keeps the application to ONE datasource: the
  extension applies one changelog per datasource, and a second owner would otherwise need a
  second datasource, whereupon an embedded engine asks which of them it belongs in.
- The changelog to apply is BUILD time configuration, so the engine's variant is filled in by
  the build (`change-log: db/changelog-@bpms@.xml`) rather than named in a profile file, which
  is read at runtime.
- Name every column explicitly in the entity. The migration and the entity have to agree, and
  a naming strategy deciding the names means they depend on a default instead of on something
  written down.

## Boilerplate files

|                               File                                |                               Purpose                                |
|-------------------------------------------------------------------|----------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM             |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support`, `quarkus-liquibase` for its own test    |
| `application/pom.xml`                                             | the BPMS adapter, `quarkus-liquibase`, `vanillabp-schema`            |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring       |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                  |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the model |

## Adding this blueprint to an existing project

1. Build the project as described in `module-single`, then apply the following.
2. Add `io.quarkus:quarkus-liquibase` to the application and `io.vanillabp:vanillabp-schema`
   to it as well. The latter contains no class, only the changelog and the SQL generated from
   it.
3. Give the workflow module a changelog below its own resource directory,
   `<workflow-module-id>/db/changelog.xml`, describing the tables of its aggregates and
   declaring `logicalFilePath="<workflow-module-id>"`.
4. Give the application a changelog which includes `vanillabp/schema/changelog.xml` from the
   classpath plus one line per workflow module. A renamed outbox table
   (`vanillabp.outbox.jdbc.table`) is set as the changelog property `vanillabp.outbox.table`
   before the include.
5. Point `quarkus.liquibase.change-log` at that changelog and set `migrate-at-start`. One
   datasource, one run.
6. Switch the runtime creators off: `quarkus.hibernate-orm.schema-management.strategy:
   validate` and `vanillabp.outbox.create-schema: false`. With an embedded Camunda 7 engine
   also `vanillabp.adapters.<adapter-id>.database-schema-update: false`, and include
   `org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml` in the application's changelog.
   Since that include is only resolvable where the engine is on the classpath, keep one
   changelog per engine and let the build name the file.
7. If the project's database is not H2, nothing changes: the changelogs describe columns
   database independently, and Liquibase writes the statements for the database in use.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

Four tests have to pass. `LoanApprovalIT` and `WorkflowOnTheOwnSchemaIT` run a real workflow,
the second one in the application, where the whole schema came from a migration. `SchemaIT` names the tables the migration was supposed to bring.
`ApplicationSmokeTest` proves the application boots with the module on the classpath.

A missing table or column reported by Hibernate or by VanillaBP is not a defect of the
framework: it means a changelog was not applied, was applied too late, or does not describe
that table. A missing column is usually the entity and the changelog disagreeing about a name.

Do not report success without having run this.
