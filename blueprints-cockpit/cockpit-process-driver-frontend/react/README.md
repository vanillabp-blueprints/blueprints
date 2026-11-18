![Header](./readme/vanillabp-headline.png)

# cockpit-process-driver-frontend-react

The forms of the user tasks of the workflow module `loan-approval` for a VanillaBP Business
Cockpit, written in React, and a dev shell with a page "Process driver". Somebody who works on a
form chooses a state of the process there. One click brings a real loan approval to that state
and opens its user task. Nobody has to click through the process to get there.

The backend is the blueprint `module-process-driver`. It has the workflow module, the process
driver and the REST API of the driver. This project takes both OpenAPI files from it, through the
git submodule `backend/`. It builds the micro-frontends with npm and nothing else, and it delivers
them as a UI jar for a Java application and as an nginx image.

The project is built like
[`cockpit-user-task-frontend-react`](https://github.com/vanillabp-blueprints/cockpit-user-task-frontend-react).
Its README explains what this one only links: micro-frontends and the two kinds of entries, why
the parts come at runtime, the module kit as the minimum, the OpenAPI file through a git
submodule and the client generated from it, why npm builds apart from Maven, and the UI jar and
the nginx image.

## What this blueprint shows

The process `loan_approval` of the backend has two user tasks. From an amount of 10000 on,
somebody has to assess the risk first. Then the contract goes to the e-signature service, and
somebody checks the signed contract. "Check contract" opens while the signature may still be on
its way, and the module refuses to complete it before the customer signed.

So a form has more than one situation to look at. To see "Check contract" without the signature,
somebody has to request a large loan, assess the risk and wait for the contract. The process
driver of the backend knows the way to each state. This project shows:

- a dev shell, a page which plays the cockpit during development: it loads the micro-frontends
  of this project with the host of the module kit, as a cockpit does,
- the page "Process driver" in the dev shell. It asks the backend for the states of the driver,
  so a new state shows up without a change here,
- a form with test data which reaches every state, sent to the driver with one click,
- a link to the user task and to the case the driver created, once the simulated cockpit knows
  them,
- a message which names the switch of the driver when the driver does not answer,
- the client of the driver generated from its OpenAPI file, like the client of the module API,
- the forms of "Assess risk" and "Check contract", and the 409 of "Check contract" while the
  signature is missing,
- browser tests of the dev shell and of the forms, without an application and without a cockpit.

### The dev shell plays the cockpit

The dev shell is a small page in the browser with three views: the process driver, a case with
its user tasks, and a user task with its form. It reads cases and user tasks from the simulated
cockpit, the dev shell simulator of the Business Cockpit. The simulator of the backend extends
it, and the workflow module reports to it, as it would to a real cockpit. The form of a user task
is drawn by `createModuleHost` of `@vanillabp/bc-module-host`. So the dev shell shows the forms
the way a cockpit with the areas `body` and `actions` shows them.

Before it draws a form, the dev shell checks the names of its web components the way a cockpit
does (`dev-shell/src/element-names.ts`). A name which does not start with the prefix of the module
and a hyphen, or which starts with the longer prefix of another module, is not loaded and not
drawn. The page shows the same note a cockpit shows instead, with the module, the name and the
prefix, and writes it to the console. So a wrong name shows up during development and not only in
the cockpit.

`npm run start` serves the dev shell and forwards the calls to the application and to the
simulated cockpit. So the page calls the same paths as in production, `/wm/loan-approval/...`,
and does not need to know any port.

A cockpit generated with the skill `business-cockpit-creator` brings a dev shell of its own, with
the task list, the case list and the switching of users. This one has only what the process
driver needs. It is a starting point, not a replacement for that dev shell.

### Why a real case and not a mocked one

The driver brings a real loan request to the state, with the business code of the module and the
answers of the simulator. The user task in the dev shell is the one the cockpit would show, with
the details the module reported. When the form completes it, the process really goes on. A form
tested against a mocked answer would not notice when the module changes what it reports.

### The driver is switched off by default

The driver creates loan approvals on request. So the backend has it only in a build with the
Maven profile `test-driver`, and only while the property `process-driver.enabled` is `true`.
Without them, every call of the driver answers 404. The page "Process driver" then says which
profile and which property switch the driver on.

## Running it

You need Node.js 24, git and Docker. Docker builds the nginx image.

Clone the repository with its submodule, which brings the OpenAPI files of the backend:

```bash
git clone --recurse-submodules https://github.com/vanillabp-blueprints/cockpit-process-driver-frontend-react.git
```

If you cloned without it, `git submodule update --init` fetches the submodule later.

### Build and test

```bash
npm ci
npx playwright install chromium-headless-shell   # once, for the browser tests
npm run build
npm test
```

|             Script              |                                                                 What it does                                                                  |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                                                                     |
| `npm run check:imports`         | fails the build on an import with `../`, see [Imports through path aliases](#imports-through-path-aliases)                                    |
| `npm run fetch:openapi`         | copies the two OpenAPI files of the backend into `build/openapi`, and takes the file of the cockpit's official API out of the cockpit's jar   |
| `npm run generate:client`       | generates the three API clients from them with `@hey-api/openapi-ts`                                                                          |
| `npm run write:bc-build`        | writes the module id and the id of this build into `src/bc-build.js`, see [The names of the web components](#the-names-of-the-web-components) |
| `npm run typecheck`             | checks the types of the whole project, the dev shell included                                                                                 |
| `npm run build:micro-frontends` | builds the entries and `bc-manifest.json` into `dist/micro-frontends`                                                                         |
| `npm run check:entries`         | runs `bc-check-entries` of the kit: no bare import, no framework in a data entry, every file there                                            |
| `npm run build:dev-shell`       | builds the dev shell into `dist/dev-shell`                                                                                                    |
| `npm run build:jar`             | builds the UI jar `io.vanillabp.blueprint:cockpit-process-driver-ui` and its pom into `dist`                                                  |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run                                                     |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                                                                          |
| `npm run start`                 | the dev shell at `http://localhost:4200`                                                                                                      |

### The dev shell with the backend

Start the simulator and the application with the driver, as the README of the backend says in
"Running it". Then start the dev shell:

```bash
npm run build
npm run start
```

Open `http://localhost:4200/#/process-driver`, choose `CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE`
and click "Create the case". After a second the page links to the user task "Check contract".
Open it and click "Approve the contract". The form says that the customer has not signed yet.

`npm run start` builds again when a file below `src/` or `dev-shell/src/` changes. Reload the page
to see the change. The dev shell expects the application at `http://localhost:8080` and the
simulated cockpit at `http://localhost:8079`. `APPLICATION_URL`, `COCKPIT_URL` and
`DEV_SHELL_PORT` change that.

## How it works

|                  File                   |                                                Role                                                 |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------|
| `dev-shell/src/process-driver-page.tsx` | the page "Process driver": states, test data, advance-to, the links and the messages                |
| `dev-shell/src/cockpit.ts`              | the host of the module kit, and the calls to the simulated cockpit                                  |
| `dev-shell/src/user-task-page.tsx`      | reads a user task from the simulated cockpit and lets the host draw its form                        |
| `dev-shell/src/element-names.ts`        | checks the names of the web components before they are drawn, the way a cockpit does                |
| `dev-shell/src/workflow-page.tsx`       | a case and its user tasks                                                                           |
| `dev-shell/src/main.tsx`                | the three views of the dev shell, chosen by the part of the address after `#`                       |
| `scripts/start.mjs`                     | serves the dev shell and the micro-frontends, and forwards the calls to the application and cockpit |
| `src/shared/process-driver-api.ts`      | `processDriver`, the one instance of the generated `ProcessDriverApi`                               |
| `src/shared/official-gui-api.ts`        | `tasklist` and `workflowlist`, the generated clients of the official API of the cockpit             |
| `src/shared/api.ts`                     | `loanApproval`, the one instance of the generated `LoanApprovalApi`, and `ResponseError`            |
| `src/data/user-task-form.ts`            | data entry `UserTaskForm`: picks the form by process and task definition                            |
| `src/shared/task-form.tsx`              | the three web components of a form: `all`, `body` and `actions`                                     |
| `src/forms/check-contract-form.tsx`     | visual entry `CheckContractForm`                                                                    |
| `test/browser/dev-shell.test.js`        | the browser tests of the dev shell                                                                  |
| `test/browser/user-task-forms.test.js`  | the browser tests of the forms in the host of the kit                                               |

### From a state to an open form

1. The page "Process driver" calls `processDriver.states()`, which is
   `GET /wm/loan-approval/api/process-driver/states`. `npm run start` forwards it to
   `/loan-approval/api/process-driver/states` of the application, the way the cockpit would. The
   answer fills the list of states.
2. "Create the case" calls `processDriver.advanceTo({ state, amount, creditScore })`. The driver
   requests a loan, assesses the risk on the way if the amount needs it, waits for the contract and
   signs it, depending on the state. It answers with the id of the loan request and of the open
   user task. In a state where the workflow ended, like `CONTRACT_DECLINED`, no task is open, and
   the answer names none.
3. The workflow module reports the workflow and the user task to the simulated cockpit after its
   transaction committed. So the page asks the cockpit again, every quarter of a second, until it
   knows both: `POST /official-api/gui/api/v1/workflow` with the id of the loan request as business
   id, and `GET /official-api/gui/api/v1/usertask/{id}`. Without an open task, the page asks only
   for the workflow and says that no user task is open.
4. The page links to `#/user-task/{id}` and to `#/workflow/{id}`. The view of the user task reads
   it from the cockpit and calls `showUserTask` of the host. The host loads `bc-manifest.json` and
   the data entry `UserTaskForm` from `/wm/loan-approval/`, then the form, and draws its web
   components into the areas `body` and `actions`.
5. The button of the form calls the module API with POST, for example
   `loanApproval.completeCheckContract(...)`, which is
   `POST /wm/loan-approval/api/{loanRequestId}/check-contract/{taskId}`. A form
   completes its task with POST, as the
   [rules for blueprints](https://github.com/vanillabp-blueprints/blueprints/blob/main/CONTRIBUTING.md#endpoints-which-a-form-calls)
   ask. The GET request on the same path stays for a person in the browser. The id of
   the loan request is the business id of the user task. The amount and the credit score come with
   the user task, from the details the module reported.

The official API of the cockpit belongs to the cockpit, not to the module. `cockpit.ts` calls it
through the clients `@hey-api/openapi-ts` generates from the OpenAPI file of the cockpit, like the
other two APIs. This project keeps no copy of that file. The cockpit ships it in its jar
`io.vanillabp.businesscockpit:official-gui-api-server`, as `v1.yaml` at the root.
`npm run fetch:openapi` downloads that jar from Maven Central and takes the file out of it. The
version is the one the backend uses, the property `businesscockpit.version` in its `pom.xml`. So
when the backend moves to another version of the cockpit, the client follows without a change
here. That version of the cockpit must be deployed before this project builds.

The jar comes from the path Maven uses,
`<repository>/<group as path>/<artifactId>/<version>/<artifactId>-<version>.jar`. A snapshot has
a time stamp in its name, so the script reads `maven-metadata.xml` first. These variables change
what the script does:

|            Variable             |                                    What it does                                    |
|---------------------------------|------------------------------------------------------------------------------------|
| `COCKPIT_REPOSITORY`            | a Maven repository of your own, for example a mirror of Maven Central              |
| `COCKPIT_REPOSITORY_TOKEN`      | the token of that repository, if it asks for a login                               |
| `BUSINESS_COCKPIT_VERSION`      | another version of the cockpit than the one of the backend                         |
| `OPENAPI_FILE_OFFICIAL_GUI_API` | the path of an OpenAPI file of the official API. Then the script downloads nothing |

### The names of the web components

All modules of a cockpit define their web components in the one registry of the page. A name
can be defined only once there, and the cockpit refuses a name which does not start with the
prefix of its module. So every name has three parts, built by `createElementNames` of the kit:

1. the prefix of the workflow module id, `loan-approval`,
2. the id of the build, the time of the build in milliseconds in base 36,
3. the BPMN process id, the task definition and the part, `form` for the area `all` and the name of the area for the others.

`elementName.userTask(BPMN_PROCESS_ID, ASSESS_RISK, 'form')` in `src/loan-approval.ts` gives `loan-approval-<build id>-loan-approval--assess-risk--form`. A new build gets new names, so the
cockpit loads it while somebody has the cockpit open, without a reload of the page.

`npm run write:bc-build` (`scripts/write-bc-build.mjs`) writes `src/bc-build.js` before the type
check: `WORKFLOW_MODULE_ID` from `config.workflowModuleId` in `package.json`, and `BUILD_ID`.
`scripts/build-micro-frontends.mjs` passes the same time to `createManifest`, so the manifest and
the names speak of one build. The file is written for every build and not checked in, its types
are in `src/bc-build.d.ts`. The browser tests read the build id from it, and
`test/build-id.test.js` proves that two builds give different names.

### Imports through path aliases

A file imports from another directory through a path alias, never with `../`. The aliases are
the `paths` of `tsconfig.json`, the same as in the other frontends, one more for each of the
two parts of the process driver, and two for the official API of the cockpit:

|             Alias             |                 Points to                 |                                   What it is                                    |
|-------------------------------|-------------------------------------------|---------------------------------------------------------------------------------|
| `@loan-approval`              | `src/loan-approval.ts`                    | the names the module shares with its backend and the cockpit                    |
| `@shared`                     | `src/shared/index.ts`                     | what the forms take from `src/shared`                                           |
| `@gui-client`                 | `src/shared/api.ts`                       | the client of the module API, and `ResponseError`                               |
| `@process-driver-client`      | `src/shared/process-driver-api.ts`        | the client of the process driver; only the dev shell imports it                 |
| `@generated`                  | `src/generated/loan-approval/index.ts`    | what the generator writes; only `src/shared/api.ts` imports it                  |
| `@generated-process-driver`   | `src/generated/process-driver/index.ts`   | likewise; only `src/shared/process-driver-api.ts` imports it                    |
| `@official-gui-client`        | `src/shared/official-gui-api.ts`          | the clients of the official API of the cockpit; only the dev shell imports them |
| `@generated-official-gui-api` | `src/generated/official-gui-api/index.ts` | likewise; only `src/shared/official-gui-api.ts` imports it                      |

The driver has an alias of its own so that a form, which imports `@gui-client`, never carries
the client of the driver. esbuild reads the same `paths` from `tsconfig.json`. `npm run build`
starts with `npm run check:imports`. `scripts/check-imports.mjs` fails the build if a file in
`src` or `dev-shell/src` imports with `../`. The generated clients are left out.

### Test ids

|             Test id             |                                 Element                                  |
|---------------------------------|--------------------------------------------------------------------------|
| `process-driver-state`          | the list of states                                                       |
| `process-driver-amount`         | the amount of the test data                                              |
| `process-driver-credit-score`   | the credit score of the test data                                        |
| `process-driver-advance`        | the button "Create the case"                                             |
| `process-driver-loan-request`   | the id of the loan request the driver created                            |
| `process-driver-open-user-task` | the link to the open user task                                           |
| `process-driver-open-workflow`  | the link to the case                                                     |
| `process-driver-no-user-task`   | the text shown when the workflow ended in the state, so no task is open  |
| `process-driver-not-in-cockpit` | the text shown when the simulated cockpit does not know the case in time |
| `process-driver-error`          | the message when the driver does not answer or the case misses the state |
| `user-task-title`               | the title of the user task in the dev shell                              |
| `workflow-user-task`            | one user task of a case, as a link                                       |
| `assess-risk-amount`            | the amount in the form "Assess risk", likewise `check-contract-amount`   |
| `assess-risk-complete`          | the button of the form, likewise `check-contract-complete`               |
| `check-contract-error`          | the message of the form, for example while the signature is missing      |

### The browser tests

`npm test` starts a small web server and Chromium. There is no application and no cockpit. The
tests answer the process driver, the official API of the cockpit and the module API inside the
browser, at the network level. One test chooses a state, creates the case, follows the link and
completes the user task in its form. Others check the message for a driver which is switched off,
for an application which does not answer, and for a case which does not reach its state. Three
check the names of the web components: a data entry which names its form without the prefix of
the module gets the note and no form, the names of this build are drawn, and the check refuses
what a cockpit refuses. The
tests of the forms load the build through `createModuleHost`, in a host with the areas `body`
and `actions` and in a host which only knows `all`.

## Documentation

- [Loading the parts of a workflow module](https://github.com/vanillabp/business-cockpit/wiki/Loading-the-parts-of-a-workflow-module): entries, the manifest, areas and the event protocol
- [The module kit](https://github.com/vanillabp/business-cockpit-module-kit): `@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host`
- [The dev shell simulator](https://github.com/vanillabp/business-cockpit/wiki/Developing-UI-components-locally#the-dev-shell-simulator): the simulated cockpit the dev shell reads from
- [`@hey-api/openapi-ts`](https://heyapi.dev/openapi-ts/get-started): the generator of the API clients

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
