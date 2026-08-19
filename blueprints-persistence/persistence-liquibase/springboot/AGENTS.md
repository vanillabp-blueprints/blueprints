# persistence-liquibase

`module-single` with the database schema taken out of the runtime's hands: Liquibase creates
every table, including the ones VanillaBP and an embedded engine would create themselves. Two
owners bring changelogs, the workflow module and the application, and one Liquibase run applies
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
`VANILLABP_TASK_DELIVERY` are VanillaBP's tables, `TXNO_OUTBOX` and `TXNO_SEQUENCE` are the
outbox library's. The delivery table's name is not configurable at all, so a renamed one is a
table nobody reads.

`loan-approval` is also the `logicalFilePath` of the module's changelog. Renaming the module
means renaming that path, and a changelog already applied somewhere must not have its path
changed: Liquibase would no longer recognize its rows and would run every changeset again.

## Core files

|                               File                                |                                                        Why it matters                                                         |
|-------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/db/changelog.xml` | the module's schema: its aggregate table. Inside the module's resource directory, because modules share one classpath         |
| `application/src/main/resources/db/changelog.xml`                 | what the application owns: `<include>` of `vanillabp/schema/changelog.xml` from the artifact, plus the outbox library's table |
| `application/src/main/resources/db/changelog-camunda7.xml`        | the same plus `<include>` of Camunda's changelog from the engine JAR. Applied by the Camunda 7 build only                     |
| `application/src/main/resources/db/gruelbox-outbox.xml`           | the outbox table of the outbox library, read out of a database its own migrator had created                                   |
| `application/src/main/java/.../SchemaConfiguration.java`          | the application's `SpringLiquibase` bean; the changelog to apply is a property the engine profile sets                        |
| `application/src/main/resources/application.yaml`                 | `ddl-auto: validate`, `vanillabp.outbox.create-schema: false`, `blueprint.schema.changelog`                                   |
| `application/src/main/resources/application-camunda7.yaml`        | `database-schema-update: false` and the changelog which includes the engine's                                                 |
| `application/src/test/java/.../SchemaIT.java`                     | asserts every table exists and that there is one bookkeeping table per owner                                                  |
| `application/src/test/java/.../MissingTableIT.java`               | asserts a forgotten migration ends the boot with VanillaBP's message                                                          |
| `application/src/test/java/.../WorkflowOnTheOwnSchemaIT.java`     | runs a workflow on the migrated schema: a table described wrongly comes out here instead of in production                     |
| `application/src/test/java/.../GruelboxSchemaDriftTest.java`      | lets the outbox library migrate an empty database and compares, so a library upgrade cannot rot the copied statements         |

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
  mistake this rule exists for.

## Boilerplate files

|                                File                                 |                                     Purpose                                     |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles and the VanillaBP BOM import                                  |
| `loan-approval/pom.xml`                                             | `vanillabp-spring-boot-support`, `spring-boot-liquibase`, `liquibase-core`      |
| `application/pom.xml`                                               | the BPMS adapter, `spring-boot-liquibase`, `liquibase-core`, `vanillabp-schema` |
| `application/src/main/java/.../Application.java`                    | the Spring Boot application, in the parent package of the module                |
| `application/src/test/resources/db/changelog-without-vanillabp.xml` | everything but VanillaBP's part, used by `MissingTableIT`                       |
| `loan-approval/src/test/java/.../TestApplication.java`              | the minimal application the module's test boots                                 |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                 |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                  |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`   | GET endpoints operating the process                                             |
| `docs/loan_approval.png`                                            | the picture of the process the README shows, rendered from the BPMN model       |

`spring-boot-liquibase` is not optional: since Spring Boot 4 the Liquibase integration is a
module of its own, and it is what makes the entity manager factory wait for a
`SpringLiquibase` bean. With `liquibase-core` alone the migration runs too late or not at all,
and the symptom is Hibernate reporting a missing table. In the module's test that
auto-configuration is what applies the module's changelog, named by
`spring.liquibase.change-log`.

## Adding this blueprint to an existing project

1. Build the project as described in `module-single`, then apply the following.
2. Add `org.springframework.boot:spring-boot-liquibase` and `org.liquibase:liquibase-core` to
   the workflow module and to the application, and `io.vanillabp:vanillabp-schema` to the
   application. The latter contains no class, only the changelog and the SQL generated from
   it.
3. Give the workflow module a changelog below its own resource directory,
   `<workflow-module-id>/db/changelog.xml`, describing the tables of its aggregates and
   declaring `logicalFilePath="<workflow-module-id>"`.
4. Give the application a changelog which includes `vanillabp/schema/changelog.xml` from the
   classpath plus one line per workflow module, and a `SpringLiquibase` bean for it keeping
   Liquibase's default bookkeeping tables. A renamed outbox table
   (`vanillabp.outbox.jdbc.table`) is set as the changelog property `vanillabp.outbox.table`
   before the include.
5. Switch the runtime creators off: `spring.jpa.hibernate.ddl-auto: validate` and
   `vanillabp.outbox.create-schema: false`. With an embedded Camunda 7 engine also
   `vanillabp.adapters.<adapter-id>.database-schema-update: false`, and include
   `org/camunda/bpm/engine/db/liquibase/camunda-changelog.xml` in the application's changelog.
   Make that include depend on the engine, since it is only on the classpath where the engine
   is: name the changelog in a property the engine's profile file sets.
6. On this platform the phase-two outbox is `com.gruelbox:transactionoutbox-core`, whose
   migrator is switched off by the same property. Create `TXNO_OUTBOX` and `TXNO_SEQUENCE`
   with your own migration. Do not write those statements from the library's source: let its
   migrator run against an empty database once, read the schema back out and copy that, then
   add a test comparing the two so an upgrade of the library fails the build.
7. If the project's database is not H2, nothing changes: the changelogs describe columns
   database independently, and Liquibase writes the statements for the database in use.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

Five tests have to pass. `LoanApprovalIT` and `WorkflowOnTheOwnSchemaIT` run a real workflow,
the second one in the application, where the whole schema came from a migration. `SchemaIT`
names the tables the migration was supposed to bring. `MissingTableIT` proves the opposite case
is reported at startup. `GruelboxSchemaDriftTest` proves the copied statements still match the
library.

A missing table reported by Hibernate or by VanillaBP is not a defect of the framework: it
means a changelog was not applied, was applied too late, or does not describe that table.

Do not report success without having run this.
