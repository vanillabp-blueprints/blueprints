# persistence-flyway

`module-single`, but the runtime no longer creates the database schema. Flyway creates every
table, including the ones VanillaBP and an embedded engine would create themselves. Three
owners bring migrations: VanillaBP, the workflow module and the application. Each of them gets
a Flyway instance and a migration history of its own.

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

`loan-approval` is also the name of the module's history table
(`flyway_schema_history_loan_approval`) and of its migration directory. Renaming the module
renames both. A history table which already holds rows must not be renamed afterwards. Flyway
would find no history and try to apply every migration again.

## Core files

|                                    File                                     |                                                            Why it matters                                                            |
|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/db/migration/V1.0.0__*.sql` | the module's schema: its aggregate table. It is inside the module's resource directory, because modules share one classpath          |
| `application/src/main/resources/db/migration`                               | where the application's own migrations go. This blueprint owns no table of its own, so the directory holds a README and nothing else |
| `application/src/main/java/.../SchemaConfiguration.java`                    | one `Flyway` plus one `FlywayMigrationInitializer` per owner, each with a history table of its own                                   |
| `application/pom.xml`, profile `camunda7`                                   | takes Camunda's scripts out of the engine JAR and names them for Flyway; the engine version is a property and the migration version  |
| `application/src/main/resources/application.yaml`                           | `ddl-auto: validate`, `vanillabp.outbox.create-schema: false`, the locations per owner                                               |
| `application/src/main/resources/application-camunda7.yaml`                  | `database-schema-update: false` and the engine's migrations, added where the engine is embedded                                      |
| `loan-approval/src/test/resources/application.yaml`                         | `spring.flyway.locations`: the module's test IS an application and applies its own migrations                                        |
| `application/src/test/java/.../SchemaIT.java`                               | reads VanillaBP's migrations, asserts their tables exist and that every owner has a history of its own                               |
| `application/src/test/java/.../MissingTableIT.java`                         | asserts a forgotten migration ends the boot with VanillaBP's message                                                                 |
| `application/src/test/java/.../WorkflowOnTheOwnSchemaIT.java`               | runs a workflow on the migrated schema: a table described wrongly comes out here instead of in production                            |

Rules which hold beyond this blueprint:

- One owner, one history table. Flyway keeps one timeline per history table and knows nothing
  about who wrote a migration. So two owners in one history compete for version numbers.
  VanillaBP ships `V2.0.0`. The day an application picks that number, one of the two loses.
- A history next to another one needs `baselineOnMigrate(true)`. Otherwise Flyway sees tables
  it has no history for and refuses to migrate at all. It also needs `baselineVersion("0")`,
  because the default baseline is version 1 and would skip every migration numbered `1.0.0`
  without a word.
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

|                               File                                |                                         Purpose                                          |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles and the VanillaBP BOM import                                           |
| `loan-approval/pom.xml`                                           | `vanillabp-spring-boot-support`; `spring-boot-flyway` and `flyway-core` for its own test |
| `application/pom.xml`                                             | the BPMS adapter, `spring-boot-flyway`, `flyway-core`, `vanillabp-schema`                |
| `application/src/main/java/.../Application.java`                  | the Spring Boot application, in the parent package of the module                         |
| `application/src/test/resources/db/no-migrations/README.txt`      | a location without migrations, used by `MissingTableIT`                                  |
| `loan-approval/src/test/java/.../TestApplication.java`            | the minimal application the module's test boots                                          |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                           |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                          |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                      |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the model                     |

`spring-boot-flyway` is required. Since Spring Boot 4 the Flyway integration is a module of its
own. Its `FlywayMigrationInitializer` makes the entity manager factory wait for a migration. A
`Flyway` bean alone migrates whenever its bean happens to be created.

## Adding this blueprint to an existing project

1. Build the project as described in `module-single`, then apply the following.
2. Add `org.springframework.boot:spring-boot-flyway` and `org.flywaydb:flyway-core` to the
   application, and to the workflow module for its own test. Add
   `io.vanillabp:vanillabp-schema` to the application. It contains no class, only the SQL and
   the changelog it was generated from.
3. Give the workflow module a migration directory of its own,
   `<workflow-module-id>/db/migration`, which describes the tables of its aggregates.
4. Declare one `Flyway` bean plus one `FlywayMigrationInitializer` per owner: VanillaBP's SQL
   (`classpath:vanillabp/schema/flyway/<database>`) with `flyway_schema_history_vanillabp`, the
   module's migrations with a history named after the module, and the application's own with the
   default history. Set `baselineOnMigrate(true)` and `baselineVersion("0")` on each of them.
5. Switch off everything which creates tables at runtime:
   `spring.jpa.hibernate.ddl-auto: validate` and `vanillabp.outbox.create-schema: false`. With
   an embedded Camunda 7 engine also
   `vanillabp.adapters.<adapter-id>.database-schema-update: false`, and let the build take
   `org/camunda/bpm/engine/db/create/activiti.<database>.create.*.sql` out of the engine JAR
   and name the files `V<engine-version>.<n>__camunda_<part>.sql`, in the order engine,
   identity, history, CMMN, DMN. Keep that in the engine's Maven profile, so that a build for a
   remote engine does not carry it.
6. If the project's database is not H2, every location changes with it: VanillaBP's SQL
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
`SchemaIT` reads VanillaBP's migrations to know which tables to expect, and checks that every
owner has a history of its own. `MissingTableIT` proves that the opposite case is reported at
startup.

If Hibernate or VanillaBP reports a missing table, that is not a defect of the framework. It
means a migration was not applied, was applied too late, or does not describe that table. If
Flyway reports a migration as skipped, `baselineVersion` was usually left at its default.

Do not report success without having run this.
