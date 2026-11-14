![Header](./readme/vanillabp-headline.png)

# The page of a case in the Business Cockpit, application

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Your users follow their cases in the
[VanillaBP Business Cockpit](https://github.com/vanillabp/business-cockpit). The cockpit lists the
cases and opens a page for each of them. The page belongs to your workflow module. This blueprint
is the application which runs such a module: it takes the workflow module and the JAR of its user
interface, and it serves both. It has no code of its own apart from the class which starts it.

## What this blueprint shows

![The loan approval process, with one user task and an event subprocess "Withdraw loan request"](docs/loan_approval.png)

A loan approval with one user task, "Review loan request", and an event subprocess "Withdraw
loan request". The process, the endpoints of the case page and what the cockpit has to know about the
module are in the backend. The case page is in the frontend. This repository puts them together.

It is built like
[`cockpit-user-task-application`](https://github.com/vanillabp-blueprints/cockpit-user-task-application-springboot),
the application of the blueprint about the forms of user tasks. Only the names differ. Its README
explains why a workflow module with a user interface has three repositories, why npm does not
build inside Maven, why the user interface is a dependency and why the application is only a
container. All of that holds here as well.

### The parts of a workflow module with a user interface

|                       Part                        |                                      What it holds                                      | Built with |
|---------------------------------------------------|-----------------------------------------------------------------------------------------|------------|
| `cockpit-workflow-details-backend-springboot`     | the API of the case page as an OpenAPI file, and the workflow module                    | Maven      |
| `cockpit-workflow-details-frontend-react`         | the case page and the columns of the case list, as a JAR of the user interface          | npm        |
| `cockpit-workflow-details-application-springboot` | this repository: the application which takes the workflow module and the user interface | Maven      |

Release them in this order: the backend, then the frontend, then this application. The two
versions this application takes are properties in `pom.xml`. A bot such as Renovate opens the
pull request which moves them from a snapshot to a release.

### The way the cockpit reaches the application

The cockpit forwards everything below `/wm/loan-approval/` to the address the module reports, its
`workflow-module-uri`, and puts the path of that address in place of `/wm/loan-approval`. The
module reports `http://localhost:8080/loan-approval`. So the API of the case page and the user
interface are both below `/loan-approval` here:

|            The browser asks the cockpit for            |                The application gets                 |     It comes from      |
|--------------------------------------------------------|-----------------------------------------------------|------------------------|
| `/wm/loan-approval/bc-manifest.json`                   | `/loan-approval/bc-manifest.json`                   | the user interface JAR |
| `/wm/loan-approval/api/cases/{loanRequestId}`          | `/loan-approval/api/cases/{loanRequestId}`          | the workflow module    |
| `/wm/loan-approval/api/cases/{loanRequestId}/withdraw` | `/loan-approval/api/cases/{loanRequestId}/withdraw` | the workflow module    |

The open user tasks of a case are not in this list. The page asks the cockpit for them, and the
cockpit answers from its own data.

## Delta to the base blueprint

Compared to [`cockpit-user-task-application`](https://github.com/vanillabp-blueprints/cockpit-user-task-application-springboot):

|             File              |                                           What is different                                            |
|-------------------------------|--------------------------------------------------------------------------------------------------------|
| `pom.xml`                     | the workflow module of `cockpit-workflow-details-backend` and the UI JAR `cockpit-workflow-details-ui` |
| `UserInterfaceSmokeTest.java` | the manifest lists `WorkflowPage` and `WorkflowList`, and the case page of a running case answers      |
| `docker-compose.yaml`         | other names of the containers                                                                          |

## Running it

Requires a JDK 21 or newer. The workflow module and the user interface JAR have to be in your
Maven repository. Until they are released, install them yourself: `mvn install` in the backend,
then `npm run build` in the frontend and `mvn install:install-file` of the JAR it built, as its
README says. Then:

```bash
mvn install
```

That runs the smoke tests on Camunda 7, which is embedded. To run on another BPMS, pick another
Maven profile. Not one line of Java changes:

```bash
mvn install -Pcamunda8
mvn install -Poperaton
mvn install -Pcibseven
```

Camunda 8 is a remote engine, so a cluster has to run. Its address and everything else specific
to that engine are in `application-camunda8.yaml`.

### With a cockpit

`docker-compose.yaml` starts a Business Cockpit and its MongoDB, as in `cockpit-user-task-application`:

```bash
mvn dependency:copy@business-cockpit
docker compose up -d
mvn spring-boot:run
```

On Docker Desktop, switch on host networking in the settings. Start a loan approval:

```
http://localhost:8080/loan-approval/api/loan-requests/start?applicant=Jane%20Doe&amount=5000
```

The cockpit on port 9080 now has the case and its user task "Review loan request". This cockpit
has no user interface of its own, but its proxy works. Log in as `jane` with the password `test`,
and open the manifest of the module and the data of the case page through the cockpit:

```
http://localhost:9080/wm/loan-approval/bc-manifest.json
http://localhost:9080/wm/loan-approval/api/cases/<loan request id>
```

A cockpit with a user interface shows the case in its list and opens the case page from there.
Stop the containers with `docker compose down`.

## How it works

|                      File                      |                                            Role                                             |
|------------------------------------------------|---------------------------------------------------------------------------------------------|
| `pom.xml`                                      | the workflow module, the user interface JAR, and per BPMS profile both adapters             |
| `src/main/java/.../Application.java`           | starts the application. The module wires itself, so the package does not matter             |
| `src/main/resources/application.yaml`          | the database, the active BPMS profile, and the cockpit the reports go to                    |
| `src/main/resources/application-camunda7.yaml` | what Camunda 7 needs, and the same file per other BPMS                                      |
| `src/test/.../ApplicationSmokeTest.java`       | the context starts, and every workflow module is wired                                      |
| `src/test/.../UserInterfaceSmokeTest.java`     | the manifest comes from the user interface JAR, and the case page of a running case answers |
| `docker-compose.yaml`                          | MongoDB, the user directory and the cockpit, on the network of your machine                 |

The application starts, and VanillaBP deploys the process of the module. The module reports
itself to the cockpit, with its `workflow-module-uri`, `ui-uri-type: ES_MODULES` and
`ui-uri-path: /bc-manifest.json`.

A loan approval starts. The cockpit adapter reports the case and its user task. A user opens the
case in the cockpit. The cockpit loads `/wm/loan-approval/bc-manifest.json`, which is the
manifest of the user interface JAR in this application. Then it loads the case page, and the page
calls `/wm/loan-approval/api/cases/...`, which is the controller of the workflow module.

The smoke tests ask for both paths without a cockpit. A missing dependency, or a path which does
not fit the module id, breaks one of them, and the application still starts. That is why the
test asks.

## Documentation

- [Connecting a workflow module](https://github.com/vanillabp/business-cockpit/wiki/Connecting-a-workflow-module): the cockpit adapter, where the reports go, and how a module registers itself
- [What a workflow module tells the cockpit](https://github.com/vanillabp/business-cockpit/wiki/Configuration#what-a-workflow-module-tells-the-cockpit): `workflow-module-uri`, `ui-uri-type`, `ui-uri-path` and the other keys
- [Running the Business Cockpit](https://github.com/vanillabp/business-cockpit/wiki/Running-the-Business-Cockpit): the cockpit server and its MongoDB
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use

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
