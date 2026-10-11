# persistence-liquibase

`module-single`, but the runtime no longer creates the database schema. Liquibase creates every
table, including the ones VanillaBP and an embedded engine would create themselves. Two owners
bring changelogs, the workflow module and the application, and one migration run applies them.
The `logicalFilePath` a changelog declares decides who owns which changeset.

Build `module-single` first and apply this delta. Nothing about the process, the aggregate or
the BPMN wiring is different.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |
| `LOAN_APPROVAL`            | the aggregate's table, in the entity AND in the module's changelog                                                        |

Four names are not placeholders and must not be renamed here: `VANILLABP_PHASE_TWO_OUTBOX`,
`VANILLABP_PHASE_TWO_OUTBOX_PAYLOAD`, `VANILLABP_TASK_DELIVERY` and `VANILLABP_HOUSEKEEPING`
are VanillaBP's tables. Each of the four can be given a name of its own at runtime, with
`vanillabp.outbox.jdbc.table`, `vanillabp.outbox.jdbc.payload-table`,
`vanillabp.outbox.jdbc.delivery-table` and `vanillabp.outbox.jdbc.housekeeping-table`. The
changelog takes the same names through its properties `vanillabp.outbox.table`,
`vanillabp.payload.table`, `vanillabp.delivery.table` and `vanillabp.housekeeping.table`, set
before the include. If you write a name on one side only, nobody reads that table. This
blueprint keeps the default names.

`loan-approval` is also the `logicalFilePath` of the module's changelog. Renaming the module
means renaming that path. But never change the path of a changelog which was already applied
somewhere. Liquibase would no longer recognize its rows and would run every changeset again.

## Core files

|                               File                                |                                                            Why it matters                                                             |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/db/changelog.xml` | the module's schema: its aggregate table. It is inside the module's resource directory, because modules share one classpath           |
| `application/src/main/resources/db/changelog.xml`                 | what the application owns: `<include>` of `vanillabp/schema/changelog.xml` from the artifact                                          |
| `application/src/main/resources/db/changelog-camunda7.xml`        | the same plus `<include>` of Camunda's changelog from the engine JAR                                                                  |
| `application/src/main/resources/db/changelog-camunda8.xml`        | the same alone: a remote engine has no tables here                                                                                    |
| `application/src/main/resources/application.yaml`                 | the changelog to apply, `strategy: validate`, `vanillabp.outbox.create-schema: false`                                                 |
| `application/src/main/resources/application-camunda7.yaml`        | `database-schema-update: false`, so the embedded engine leaves its tables to Liquibase                                                |
| `loan-approval/src/test/resources/application.yaml`               | the module's own changelog and `validate`: its test is an application which applies it                                                |
| `application/src/test/java/.../SchemaIT.java`                     | reads the artifact's changelog, asserts that the tables it describes exist, and that every owner can be told apart in the one history |
| `application/src/test/java/.../WorkflowOnTheOwnSchemaIT.java`     | runs a workflow in the application, which is the only place where two datasources and a migrated schema meet                          |

Rules which hold beyond this blueprint:

- Never edit a changeset which was applied somewhere. Liquibase compares checksums and refuses
  to run, and repairing that means manual work in a production database. Add a new changeset
  instead, with the version which introduces it in its id.
- Never copy VanillaBP's or the engine's statements into the application. Both ship them, and
  both decide by their version what is correct. `<include>` reaches into a JAR on the
  classpath.
- Ownership is identity, not a table. A changelog declares its own `logicalFilePath`. Liquibase
  records its changesets under that path, not under the file which included them. So one
  history holds every owner, and a module can still be upgraded on its own. A changelog without
  that attribute is recorded under whatever file included it. This rule exists to prevent that
  mistake. It also keeps the application at ONE datasource. The extension applies one changelog
  per datasource. Otherwise a second owner would need a second datasource, and then an embedded
  engine asks which of them it belongs in.
- The changelog to apply is configured at BUILD time. So the build fills in the engine's
  variant (`change-log: db/changelog-@bpms@.xml`). A profile file would not work, because it is
  read at runtime.
- Name every column explicitly in the entity. The migration and the entity have to agree. If a
  naming strategy decides the names, they depend on a default instead of on something written
  down.

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
2. Add `io.quarkus:quarkus-liquibase` and `io.vanillabp:vanillabp-schema` to the application.
   The second contains no class, only the changelog and the SQL generated from it.
3. Give the workflow module a changelog below its own resource directory,
   `<workflow-module-id>/db/changelog.xml`. It describes the tables of its aggregates and
   declares `logicalFilePath="<workflow-module-id>"`.
4. Give the application a changelog which includes `vanillabp/schema/changelog.xml` from the
   classpath, plus one line per workflow module. If you rename the outbox table
   (`vanillabp.outbox.jdbc.table`), set the changelog property `vanillabp.outbox.table` before
   the include. Do the same for the payload table and the delivery table, through
   `vanillabp.payload.table` and `vanillabp.delivery.table`.
5. Point `quarkus.liquibase.change-log` at that changelog and set `migrate-at-start`. One
   datasource, one run.
6. Switch off everything which creates tables at runtime:
   `quarkus.hibernate-orm.schema-management.strategy: validate` and
   `vanillabp.outbox.create-schema: false`. With an embedded Camunda 7 engine also
   `vanillabp.adapters.<adapter-id>.database-schema-update: false`, and include
   `org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml` in the application's changelog.
   That include only resolves where the engine is on the classpath. So keep one changelog per
   engine and let the build name the file.
7. If the project's database is not H2, nothing changes. The changelogs describe columns
   independently of the database, and Liquibase writes the statements for the database in use.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

Four tests have to pass. `LoanApprovalIT` and `WorkflowOnTheOwnSchemaIT` run a real workflow.
The second one runs in the application, where the whole schema came from a migration.
`SchemaIT` reads the artifact's changelog to know which tables to expect.
`ApplicationSmokeTest` proves that the application boots with the module on the classpath.

If Hibernate or VanillaBP reports a missing table or column, that is not a defect of the
framework. It means a changelog was not applied, was applied too late, or does not describe
that table. A missing column usually means that the entity and the changelog disagree about a
name.

Do not report success without having run this.
