# module-multi

An application built from two workflow module JARs plus a library they share. Each JAR
carries an index of its classes, so the application knows nothing about them beyond the
dependency. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It carries the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow modules                                                                                      |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Names this blueprint adds, because it has more than one of everything:

|          Name           |                                       What it is                                        |
|-------------------------|-----------------------------------------------------------------------------------------|
| `loanrepayment`         | the second use case, Java package (`loan-repayment` kebab, `loan_repayment` process ID) |
| `banking-commons`       | the library both modules depend on, Java package `blueprint.commons`                    |
| `blueprint.application` | the package the application's tests live in                                             |

**The rules this blueprint is built on:**

1. A workflow module makes its own classes visible. The index (`jandex-maven-plugin`) is
   what a JAR needs for its beans to exist; the marker file alone only makes the module and
   its resources known.
2. Everything two modules share a namespace in has to be named per module: Java package,
   resource directory, bean names, JPA entity name, REST path, and the identifiers the BPMS
   sees (`name-clash-avoidance`).
3. A shared library holds helpers, value objects and clients. It never holds an aggregate, a
   BPMN model, a `ProcessService` or a task handler.

## Core files

|                                 File                                 |                                             Why it matters                                             |
|----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `loan-approval/pom.xml`, `loan-repayment/pom.xml`                    | the index plugin, which is what makes a module's classes visible to the application's build            |
| `loan-repayment/...`                                                 | the same module structure a second time; the copy is the point, there is no shared base class          |
| `banking-commons/src/main/java/blueprint/commons/*.java`             | the shared library: a value object and a bean, indexed like a module because it is a JAR like a module |
| `application/src/main/resources/application-camunda7.yaml`           | `name-clash-avoidance: use-prefix`, the setting that keeps two modules apart inside the engine         |
| `application/src/test/java/.../ApplicationSmokeTest.java`            | counts the modules: one `ProcessService` bean per module, not "at least one"                           |
| `application/src/test/java/.../ModuleConfigurationPerProfileIT.java` | proves `loan-approval-test.yaml` wins over `loan-approval.yaml` while the profile is active            |

## Boilerplate files

|                                File                                 |                                                    Purpose                                                    |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the four Maven modules, the BPMS profiles and the VanillaBP BOM import                                        |
| `loan-approval/pom.xml`, `loan-repayment/pom.xml`                   | `vanillabp-quarkus-support`, the shared library and the index plugin, never an adapter outside the test scope |
| `banking-commons/pom.xml`                                           | no VanillaBP dependency at all: a library shared by modules knows nothing about processes                     |
| `application/pom.xml`                                               | two module dependencies, the VanillaBP extension and the BPMS adapter                                         |
| `application/src/main/resources/application.yaml`                   | the database and the profile, and nothing about either module                                                 |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the module's own configuration, loaded by its file name                                                       |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of a module's integration test, identical in every blueprint                                       |
| `docs/loan_approval.png`, `docs/loan_repayment.png`                 | the pictures of the two processes the README shows, rendered from the BPMN models                             |

`WorkflowModuleTest` is identical in every blueprint - copy it unchanged, once per workflow
module. Every test class carries `@QuarkusTest` itself; inheriting it from the base class is
not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Build `module-single` first. A second workflow module is a delta on that, not a different
   kind of project.
2. Copy the module you have into a second Maven module: own artifact, own Java package, own
   resource directory named after its own module ID, own `META-INF/workflow-module`.
3. Give both modules the index plugin. Without it their classes are invisible to the build of
   the application, the `@WorkflowService` of the module is never found, and the build ends
   naming a process without code behind it.
4. Rename what collides, and expect the compiler to stay silent about all of it:
   - JPA entities: `@Entity(name = "LoanRepayment")`, because one persistence unit cannot hold
     two entities called `Aggregate`. Beans themselves are resolved by type and need nothing.
   - REST paths, one per module.
5. Decide how the BPMS keeps the identifiers apart, in the application's configuration:
   `vanillabp.adapters.<id>.name-clash-avoidance` with `use-prefix`, `by-adapter` or `none`.
   Decide it before the first workflow runs - changing it later is a migration, because
   running workflows are found by the identifiers they were started with.
6. Put shared code in a library JAR, and keep aggregates, BPMN models, `ProcessService`s and
   task handlers out of it. Two modules needing the same aggregate are one module.
7. Give each module its configuration per environment as `<module-id>-<profile>.yaml` next to
   `<module-id>.yaml`. The profile is part of the file name, and a test runs in the profile
   `test`, so that file is what a test sees.
8. Extend the application's smoke test to count the modules. "At least one `ProcessService`"
   passes with a forgotten module.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure.

```bash
mvn install verify -Pcamunda8
```

`-Pcamunda8` needs a running cluster and `vanillabp.adapters.camunda8.rest-address`
configured; do not report a failure of that profile as a defect of the generated code before
having checked it.

All four tests have to pass: the integration test of each module, the smoke test counting two
`ProcessService` beans, and `ModuleConfigurationPerProfileIT`. A build which ends naming a
BPMN process without code behind it means a module has no index.

Do not report success without having run this.
