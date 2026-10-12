# module-packaging

A project which assembles a runtime from workflow module JARs which it has only as
dependencies: foreign group, foreign version, foreign packages, no sources. The application is
a POM, a configuration file and two tests. A delta on top of `module-multi`, whose modules
these are.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

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
| `com.acme`              | the packages and groups of the foreign modules: `com.acme.lending`, `com.acme.banking`  |
| `blueprint.application` | the assembling application's package, which reaches none of the modules                 |

**The rules this blueprint is built on:**

1. The application names no module in code. No scan, no entity list, no property of a module.
   Only dependencies, their pinned versions, the adapter and what the environment decides. If
   something else is needed, that is a finding, not a workaround.
2. A module is a released artifact: own group, own version, own build, own tests. It is
   updated by changing a version, and nothing in the application moves with it.
3. Configuration has three levels in this order. What comes from outside beats the
   `application.yaml` of the assembling project. That beats the file a module brings along,
   which carries its defaults. A module's profile-specific file still beats its plain one.
   Early VanillaBP 2 snapshots had it the opposite way, so older projects may assume that. The
   order is
   [documented here](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration).
4. Everything about having two modules at all comes from `module-multi` and is not repeated
   here.

## Core files

|                             File                              |                                                      Why it matters                                                      |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `application/pom.xml`                                         | the whole project: two module dependencies, their versions pinned in `dependencyManagement`, the adapter, the image name |
| `application/src/main/resources/application.yaml`             | the database, the profile, and two attempts to override values a module ships                                            |
| `application/src/test/java/.../ApplicationSmokeTest.java`     | the discovery test: every declared workflow module has to be one VanillaBP wired                                         |
| `application/src/test/java/.../ConfigurationLevelsIT.java`    | which of the three configuration levels arrives: outside beats the application, the application beats the module         |
| `loan-approval/pom.xml`                                       | a foreign JAR's POM: no parent, own group `com.acme.lending`, own version, own build and tests                           |
| `loan-approval/src/main/java/com/acme/loanapproval/...`       | the module's code, in the package its team chose. No scan of the application reaches it                                  |
| `loan-approval/src/main/resources/META-INF/spring/...imports` | what makes that module usable without the application knowing anything about it                                          |

## Boilerplate files

|                          File                           |                                      Purpose                                      |
|---------------------------------------------------------|-----------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                              | lists the foreign modules only so the blueprint builds after a plain clone        |
| `banking-commons/pom.xml`                               | the library the modules share, again with coordinates of its own                  |
| `loan-approval/src/main/resources/loan-approval/*.yaml` | the module's own configuration, shipped inside its JAR                            |
| `loan-approval/src/test/java/com/acme/...`              | the module's own tests, owned by its team and running without this application    |
| `docs/loan_approval.png`, `docs/loan_repayment.png`     | the pictures of the two processes the README shows, rendered from the BPMN models |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Here they are in the package of whoever owns them: the harness of a module in the
module's own package, the application's smoke test next to the application.

## Adding this blueprint to an existing project

1. Take the workflow modules as they are published: dependency plus version, nothing else. If a
   module needs the application to declare its packages, it is not ready to be published. Ask
   its team for the wiring it should bring. Read the fallback in the README before you accept
   the workaround.
2. Pin every module version in `dependencyManagement` of the assembling project. Then a module
   release changes one line there, and the diff of an update says what changed.
3. Put nothing specific to a module into the application: no scan, no entity list, no property
   of a module. What belongs there is the database, the BPMS adapter and what the environment
   decides.
4. Keep the smoke test which counts the modules. In a project which assembles foreign JARs, it
   tests the discovery. Without it, a forgotten dependency looks exactly like a working
   application.
5. Keep the order in mind when you configure. A module's file carries defaults. So the
   assembling project sets what its environment needs, and it does not have to ask the module's
   team for a release. What really varies per deployment still belongs outside, in an
   environment variable, because that beats both.
6. Ship what the platform ships: the executable artifact from the normal build, and the image
   from the platform's own tooling. Neither belongs in `verify`. An image build takes minutes,
   and a test which starts a container tests the container runtime.
7. Choose the BPMS (business process management system) with a Maven profile, as everywhere
   else. It decides what is packaged, what the tests run against and what the image contains.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure.

```bash
mvn install verify -Pcamunda8
```

`-Pcamunda8` needs a running cluster and a configured
`vanillabp.adapters.camunda8.rest-address`. Check both before you report a failure of that
profile as a defect of the generated code.

All tests have to pass: the two module tests, the smoke test and `ConfigurationLevelsIT`. If
the smoke test fails with a module which is declared but not wired, the discovery broke. Either
the module has no auto-configuration, or its JAR did not get onto the classpath.

The image is not built by `verify`. Build it once by hand when changing anything about the
packaging:

```bash
mvn -pl application spring-boot:build-image
```

Do not report success without having run this.
