![Header](./readme/vanillabp-headline.png)

# An application built from several workflow modules

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

One workflow module is a JAR. Two are an application built out of parts, and that changes
three things: the modules need namespaces of their own, their identifiers have to be kept
apart inside the BPMS, and each of them has to bring its wiring along instead of relying on
where the application happens to sit in the package tree. A delta on top of `module-single`.

The application in this blueprint contains no code. Not a scan, not a configuration of a
module, not a single import: it is a POM with two dependencies, and everything else the
modules bring themselves.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

![The loan repayment process](docs/loan_repayment.png)

Two use cases of an online banking application: a loan is approved, and later it is paid
back. Each is a workflow module of its own, with its own process, its own aggregate and its
own configuration, and neither imports a class of the other.

### Each module wires itself

A Spring Boot application scans its own package and everything below it. That is what the
other blueprints rely on, and it works as long as the application sits above the module. It
stops working the moment a module is what it is meant to be: a JAR from somewhere else,
published by another team, living in a package this application never heard of.

So each module brings an auto-configuration:

```java
@AutoConfiguration
@ComponentScan(nameGenerator = LoanApprovalBeanNames.class)
@EntityScan
@EnableJpaRepositories(nameGenerator = LoanApprovalBeanNames.class)
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class LoanApprovalAutoConfiguration {
}
```

registered in the file Spring Boot reads from every JAR on the classpath:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

That is the whole recipe, and it is identical in both modules. Together with
`META-INF/workflow-module` these are the only two resources of a module which are not below
its own directory, and both are locations the platform prescribes.

**What happens without it** is worth knowing, because the symptom does not name the cause.
VanillaBP scans the classpath for `@WorkflowService` classes itself, so the process is found
and its BPMN is deployed; the beans behind it are not. The application then fails while
wiring a task to the method implementing it, and the message names a missing bean rather
than a missing module.

**The module's own test boots it the same way.** `ModuleTestApplication` is a
`@SpringBootConfiguration` with `@EnableAutoConfiguration` and no component scan at all: the
module is wired by its auto-configuration, exactly as in an application which consumes it. A
scan there would test a wiring nobody uses, and it would register every bean twice, once
under the scanned name and once under the generated one.

### Two modules, one classpath, one engine

Nothing isolates two workflow modules from each other. They share a classpath, a database, an
HTTP port and a BPMS, so everything they name has to be unique, and this blueprint shows
where that bites:

- **Java packages and resources.** `blueprint.workflowmodule.loanapproval` and
  `blueprint.workflowmodule.loanrepayment`, and below `src/main/resources` one directory per
  module ID. A resource at the classpath root works in a single-module application and
  collides in this one, which is why the rule exists before it hurts.
- **Bean names.** Every use case of the reference structure has a class called `Service` and
  one called `ApiController`, and Spring names a bean after its class. The second module to
  be registered then ends the boot with *conflicts with existing, non-compatible bean
  definition*, a message about a name nobody wrote. Each module therefore generates its bean
  names as `<module-id>_<SimpleName>`, so they read `loan-approval_Service` and
  `loan-repayment_Service`. It is one class per module saying which module it is
  (`LoanApprovalBeanNames`), it covers the repositories through the same generator, and it
  is what a stack trace or an actuator listing shows from then on. `BeanNamesPerModuleIT`
  keeps it that way, because falling back to the default names works until somebody adds a
  third module.
- **JPA entities.** Both aggregates are called `Aggregate`, and one persistence unit cannot
  hold two entities of that name: `@Entity(name = "LoanRepayment")` gives the second one a
  name of its own, and the tables differ anyway.
- **HTTP paths.** `/api/loan-approval/...` and `/api/loan-repayment/...`, each named after
  its module.
- **Identifiers inside the BPMS.** Two modules may legitimately use the same BPMN process ID
  or message name. What keeps them apart is `name-clash-avoidance`, and this is the blueprint
  where it stops being a footnote.

### Keeping identifiers apart in the BPMS

This blueprint sets one mode and explains the others, because the choice belongs to the
operator rather than to the code:

```yaml
vanillabp:
  adapters:
    camunda7:
      name-clash-avoidance: use-prefix
```

- `use-prefix`: VanillaBP prefixes every identifier with the workflow module ID before it
  reaches the engine and strips it off again on the way back. Nothing else notices:
  the BPMN files, the business code and the configuration all keep the plain names. No
  tenant is involved, which matters on a BPMS licensed per tenant.
- `by-adapter`: the engine's own isolation, a tenant per workflow module. The stronger
  separation, and the more expensive one.
- `none`: nothing is scoped, and the application promises that its identifiers are unique.
  The adapter warns about it at startup, and
  `vanillabp.adapters.<id>.accept-unscoped-identifiers: true` is how that promise is given.

**Changing the mode later is a migration, not a setting.** The identifiers a running workflow
was started with are the ones it is found by, so a switch leaves them unreachable. The way
across is a second adapter ID differing only in this setting, put first in
`vanillabp.prioritized-adapters`: new workflows start in the new mode, the running ones are
finished in the old one. [The wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided)
has the details.

### Shared code, and what must never be shared

`banking-commons` is a library JAR both modules depend on. It holds a value object (`Money`)
and a client for a system next door (`CustomerDirectory`), and it wires itself the same way a
module does, for the same reason.

What belongs in there is what has no opinion about a business case: technical helpers, value
objects, clients for systems both use cases talk to.

**What must never be shared:**

|        Never shared         |                                     Why                                     |
|-----------------------------|-----------------------------------------------------------------------------|
| a workflow aggregate        | it is the state of one business case, and sharing it merges the two         |
| a BPMN model                | a process belongs to exactly one module, and a copy is cheaper than a link  |
| a `ProcessService`          | it is typed by the aggregate, so sharing it means sharing that              |
| a `@WorkflowTask` handler   | it implements a task of one process, and that process belongs to one module |
| an entity of another module | it makes one module's schema the other one's contract                       |

Two modules needing the same aggregate are one module, not two. Two modules needing to talk
to each other is a different blueprint,
[`module-interaction`](https://github.com/vanillabp-blueprints/module-interaction-springboot),
and the answer there is an API, never a shared class.

### A module brings its configuration per environment

Next to `loan-approval/loan-approval.yaml` sit `loan-approval-test.yaml` and
`loan-approval-prod.yaml`, and the active profile decides which of them applies on top of the
base file. Values only the module knows stay with the module; the application contributes the
things an environment really determines, and secrets come from outside the JAR entirely.

The profile lives in the **file name**. Inside a module's configuration file
`spring.config.activate.on-profile` has no effect, so a multi-document YAML would silently
deliver the wrong value. `ModuleConfigurationPerProfileIT` is the test which would notice.

Schema ownership belongs into this list and is deliberately missing from this blueprint: a
module which needs tables should manage them itself, initialized by its auto-configuration.
Showing it means bringing a migration tool in, which shifts what this blueprint is about, so
it stays with `ddl-auto` and the topic gets blueprints of its own.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-springboot):

|                              File                               |                                         What is different                                          |
|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `loan-repayment/`                                               | the second workflow module, same structure, other use case, no shared code with the first          |
| `banking-commons/`                                              | the library both modules use, plus the auto-configuration contributing its bean                    |
| `.../loanapproval/LoanApprovalAutoConfiguration.java`           | new: the module contributes its beans, entities, repositories and properties itself                |
| `loan-approval/src/main/resources/META-INF/spring/...imports`   | new: the file which makes the auto-configuration one                                               |
| `.../loanapproval/*.java`                                       | every bean carries a name of its own, because the other module has classes of the same name        |
| `loan-approval/.../loan-approval-test.yaml`, `-prod.yaml`       | new: the module's configuration per environment                                                    |
| `application/.../Application.java`                              | moved to `blueprint.application`: the application is no longer above the modules, and needs not be |
| `application/src/main/resources/application-camunda7.yaml`      | `name-clash-avoidance: use-prefix`, because two modules can collide                                |
| `application/src/test/.../ApplicationSmokeTest.java`            | asserts one `ProcessService` per module instead of "at least one"                                  |
| `application/src/test/.../ModuleConfigurationPerProfileIT.java` | new: proves that the module file of the active profile is the one that applies                     |

Everything else is the base blueprint: the wiring classes, the aggregates, the tests of the
modules, the way the BPMS is chosen.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile, not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run; its address lives in
`application/src/main/resources/application-camunda8.yaml`, with a copy for each module's own
test.

Start the application:

```bash
mvn -pl application spring-boot:run
```

Two URLs start something, one per workflow module:

```
http://localhost:8080/api/loan-approval/start?customerId=C-1001&amount=5000
http://localhost:8080/api/loan-repayment/start?customerId=C-1002&amount=6000
```

Each answers with the ID of the case it started and logs the URL showing the result:

```
Loan approval '0f7c…' started: Ada Lovelace asks for 5,000 EUR
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

The names in those lines come from `banking-commons`, which is the shortest demonstration of
what a shared library is for: both modules ask the same directory the same question.

While the application runs on Camunda 7, Camunda's own web applications are served at
`http://localhost:8080/camunda`, user `demo` / `demo`. Cockpit is also where prefixing
becomes visible: the deployed processes are named `loan-approval-loan_approval` and
`loan-repayment-loan_repayment` there, while everything in this repository keeps calling them
`loan_approval` and `loan_repayment`.

## How it works

|                                 File                                 |                                           Role                                           |
|----------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `application/pom.xml`                                                | the whole application: two module dependencies and the BPMS profile deciding the adapter |
| `application/.../Application.java`                                   | boots; its package is deliberately not above the modules                                 |
| `application/src/main/resources/application.yaml`                    | the database and the profile, and nothing about either module                            |
| `loan-approval/.../LoanApprovalAutoConfiguration.java`               | what the module contributes: beans, entities, repositories, properties                   |
| `loan-approval/src/main/resources/META-INF/spring/...imports`        | makes that class an auto-configuration Spring Boot picks up from the JAR                 |
| `loan-approval/src/main/resources/loan-approval/loan-approval*.yaml` | the module's configuration, one file per environment, named by profile                   |
| `loan-repayment/...`                                                 | the same structure once more, for the second use case                                    |
| `banking-commons/.../Money.java`, `.../CustomerDirectory.java`       | the shared library: a value object and a client, nothing a process is made of            |
| `banking-commons/.../BankingCommonsAutoConfiguration.java`           | contributes the client, and steps aside for an application which brings its own          |
| `application/src/test/.../ApplicationSmokeTest.java`                 | boots the application and counts the modules: one `ProcessService` bean per module       |
| `application/src/test/.../ModuleConfigurationPerProfileIT.java`      | proves the module file of the active profile wins over the module's base file            |
| `loan-approval/src/test/.../LoanApprovalIT.java`                     | the module's own test, against the module alone, exactly as in the base blueprint        |

The order of events inside a module is the one of the base blueprint and does not change with
the second module: the API calls `Service`, `Service` tells `Workflow` what happened,
VanillaBP persists the aggregate and starts the process in the same transaction, and the
service task arrives in `WorkflowTaskHandler`.

What is new is what happens before any of that: Spring Boot reads the auto-configuration
imports of every JAR, each module contributes its beans, and VanillaBP finds two workflow
modules, deploys the BPMN files of both, and creates one `ProcessService` per aggregate. Add
a third module and none of the existing files change.

## Documentation

- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
- [Defining a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#defining-a-workflow-module): the marker file, the resource conventions and the module's own configuration files
- [Publishing a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#publishing-a-workflow-module-it-brings-its-own-wiring): the auto-configuration recipe, the symptoms without it, and the names which collide once two modules meet
- [How name clashes are avoided](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided): the three modes, and why changing the mode is a migration
- [Configuration of a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#configuration): the file names, the profiles and the priority order
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: how a BPMN task has to be modelled for that engine

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

        https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the License.
