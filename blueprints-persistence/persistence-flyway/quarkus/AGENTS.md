# persistence-flyway

`module-single`, but the runtime no longer creates the database schema. Flyway creates every
table, including the ones VanillaBP and an embedded engine would create themselves. Three
owners bring migrations: VanillaBP, the workflow module and the application. The extension
applies them in one run with one history table, so their version numbers have to stay apart.

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
| `LOAN_APPROVAL`            | the aggregate's table, in the entity AND in the module's migration                                                        |

Four names are not placeholders and must not be renamed here: `VANILLABP_PHASE_TWO_OUTBOX`,
`VANILLABP_PHASE_TWO_OUTBOX_PAYLOAD`, `VANILLABP_TASK_DELIVERY` and `VANILLABP_HOUSEKEEPING`
are VanillaBP's tables. Each of the four can get a name of its own at runtime, with
`vanillabp.outbox.jdbc.table`, `vanillabp.outbox.jdbc.payload-table`,
`vanillabp.outbox.jdbc.delivery-table` and `vanillabp.outbox.jdbc.housekeeping-table`. The SQL
in `io.vanillabp:vanillabp-schema` cannot follow such a name. It is generated while that
artifact is built and carries the defaults. So whoever renames a table edits the generated
statements as well. A table renamed on one side only is a table nobody reads. This blueprint
keeps the default names.

`loan-approval` is also the name of the module's migration directory. It is one of the
locations the extension reads. Renaming the module renames the directory and the location with
it.

## Core files

|                                    File                                     |                                                           Why it matters                                                            |
|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/db/migration/V1.0.0__*.sql` | the module's schema: its aggregate table. It is inside the module's resource directory, because modules share one classpath         |
| `application/pom.xml`, profile `camunda7`                                   | takes Camunda's scripts out of the engine JAR and names them for Flyway; the engine version is a property and the migration version |
| `application/src/main/resources/application.yaml`                           | the locations per owner, `strategy: validate`, `vanillabp.outbox.create-schema: false`, `quarkus.flyway.migrate-at-start: false`    |
| `application/src/main/resources/application-camunda7.yaml`                  | `database-schema-update: false` and the engine's migrations, added where the engine is embedded                                     |
| `loan-approval/src/test/resources/application.yaml`                         | `quarkus.flyway.locations`: the module's test IS an application and applies its own migrations                                      |
| `application/src/test/java/.../SchemaIT.java`                               | reads VanillaBP's migrations, asserts their tables exist and that every owner is in the one history                                 |
| `application/src/test/java/.../WorkflowOnTheOwnSchemaIT.java`               | runs a workflow on the schema the migration built                                                                                   |

Rules which hold beyond this blueprint:

- One history, and the version numbers have to stay apart. Flyway keeps one timeline per
  history table and knows nothing about who wrote a migration. VanillaBP numbers its own in
  2.x, and the engine uses its version. The application and its modules pick their own. A
  collision ends the boot with "Found more than one migration with version X", so it is loud,
  not silent.
- Do not try to give an owner a history of its own here. The extension applies ONE
  configuration per datasource. A named configuration without a datasource of the same name is
  ignored, even with a `jdbc-url`. And a migration run of the application's own comes too late:
  Hibernate builds its session factory, and with `validate` compares it with the schema, before
  any startup observer runs. So a history per owner costs either a datasource per history or
  the startup validation.
- Never edit a migration which was applied somewhere. Flyway compares checksums and refuses to
  run, and repairing that means manual work in a production database. Add a new migration
  instead.
- Never copy VanillaBP's or the engine's statements into the application. VanillaBP ships them
  in `vanillabp-schema`, the engine in its own JAR, and both decide by their version what is
  correct. Where Flyway needs another name, the build renames a copy in `target`, not in the
  sources.
- Name every column explicitly in the entity. The migration and the entity have to agree. If a
  naming strategy decides the names, they depend on a default instead of on something written
  down.
- Every location names the database (`.../flyway/h2`, `activiti.h2.create.*`). Flyway does not
  hide the differences between SQL dialects. So a project on another database changes those
  locations and takes the matching files.

## Boilerplate files

|                               File                                |                               Purpose                                |
|-------------------------------------------------------------------|----------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM             |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support`; `quarkus-flyway` for its own test       |
| `application/pom.xml`                                             | the BPMS adapter, `quarkus-flyway`, `vanillabp-schema`               |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring       |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                  |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the model |

## Adding this blueprint to an existing project

1. Build the project as described in `module-single`, then apply the following.
2. Add `io.quarkus:quarkus-flyway` and `io.vanillabp:vanillabp-schema` to the application. The
   second contains no class, only the SQL and the changelog it was generated from.
3. Give the workflow module a migration directory of its own,
   `<workflow-module-id>/db/migration`, which describes the tables of its aggregates.
4. Point `quarkus.flyway.locations` at one directory per owner, and set `migrate-at-start`. The
   owners are VanillaBP's SQL (`classpath:vanillabp/schema/flyway/<database>`), the migrations
   of every workflow module, the application's own, and `classpath:db/migration-engine` for an
   embedded engine.
5. Keep the version numbers of the owners apart, because they share one timeline. Do not
   renumber VanillaBP's or the engine's. Give the application and its modules ranges of their
   own.
6. Switch off everything which creates tables at runtime:
   `quarkus.hibernate-orm.schema-management.strategy: validate` and
   `vanillabp.outbox.create-schema: false`. With an embedded Camunda 7 engine also
   `vanillabp.adapters.<adapter-id>.database-schema-update: false`, and let the build take
   `org/camunda/bpm/engine/db/create/activiti.<database>.create.*.sql` out of the engine JAR
   and name the files `V<engine-version>.<n>__camunda_<part>.sql`, in the order engine,
   identity, history, CMMN, DMN. Keep that in the engine's Maven profile, so that a build for a
   remote engine does not carry it.
7. If the project's database is not H2, every location changes with it: VanillaBP's SQL
   directory, the engine's script names, and any statement written by hand. Flyway does not
   hide the differences between SQL dialects. That is the price of its simplicity.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

Four tests have to pass. `LoanApprovalIT` and `WorkflowOnTheOwnSchemaIT` run a real workflow.
The second one runs in the application, where the whole schema came from a migration.
`SchemaIT` reads VanillaBP's migrations to know which tables to expect. `ApplicationSmokeTest`
proves that the application boots with the module on the classpath.

If Hibernate or VanillaBP reports a missing table or column, that is not a defect of the
framework. It means a migration was not applied, was applied too late, or does not describe
that table. A missing column usually means that the entity and the migration disagree about a
name. A location missing from `quarkus.flyway.locations` shows up as a table nobody created.

Do not report success without having run this.
