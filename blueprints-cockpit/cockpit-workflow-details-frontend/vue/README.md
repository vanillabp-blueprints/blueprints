![Header](./readme/vanillabp-headline.png)

# cockpit-workflow-details-frontend-vue

The page of a case for a VanillaBP Business Cockpit, written in Vue, and the columns of the
case list. The cockpit loads them at runtime from the workflow module `loan-approval`. This
project builds them with npm and nothing else, and it delivers them in two ways: as a UI jar for
a Java application, and as an nginx image.

It is one of three parts of the blueprint `cockpit-workflow-details`:

|                  Part                   |                                   What it holds                                   |
|-----------------------------------------|-----------------------------------------------------------------------------------|
| `cockpit-workflow-details-backend`      | the workflow module and its API as an OpenAPI file, built with Maven              |
| `cockpit-workflow-details-frontend-vue` | this project: the micro-frontends, the UI jar and the nginx image, built with npm |
| `cockpit-workflow-details-application`  | takes the workflow module and the UI jar and makes a runnable application of them |

The project is built like
[`cockpit-user-task-frontend-vue`](https://github.com/vanillabp-blueprints/cockpit-user-task-frontend-vue),
which shows the forms of user tasks. Its README explains what this one only links: micro-frontends
and the two kinds of entries, why the parts come at runtime, the module kit as the minimum, the
OpenAPI file through a git submodule and the client generated from it, why npm builds apart from
Maven, and the UI jar and the nginx image. The same case page in React is
[`cockpit-workflow-details-frontend-react`](https://github.com/vanillabp-blueprints/cockpit-workflow-details-frontend-react).
Both have the same tests and the same contract with the module kit.

## What this blueprint shows

The process `loan_approval` has one user task, "Review loan request", and an interrupting event
subprocess "Withdraw loan request". The case page shows where a case stands and lets the user
withdraw the loan request.
This project shows:

- one data entry `WorkflowPage` which picks the page of a case by its process,
- the page as a visual entry of its own, which the cockpit only loads when somebody opens a case,
- the page for a cockpit with areas (`body`, `information`, `actions`) and the same page for a
  cockpit without areas (`all`),
- the open user tasks of the case in `body`, from the cockpit through `workflow.getUserTasks(...)`,
  without a client of its own for the API of the cockpit,
- the data of the loan request in `information`, from the module API,
- the button "Withdraw loan request" in `actions`, which calls the module API. The module ends
  the case through the event subprocess, and the cockpit learns about it from the module,
- areas which talk to each other: after "Withdraw loan request", `actions` tells `body` and
  `information`
  to load their data again,
- long content which wraps instead of widening the page,
- the applicant and the status in two columns of the case list,
- Vue inside web components: a small Vue application for each of them, and single-file
  components named `*.ce.vue` whose styles go into the shadow root,
- browser tests which load the build the way a cockpit does, without a cockpit.

### The page of a case

The cockpit has a page for every case. Unlike the page of a user task, it has no header with
buttons of the cockpit. All of the page belongs to the workflow module. A cockpit with areas puts
`information` at the top of a column on the right and `actions` at its bottom, and the column
stays in place while a long `body` scrolls. A cockpit without areas shows the web component of
`all`, which arranges everything itself.

### Withdrawn is a status, not an end reason

A withdrawn loan request is a business outcome. The process still ends the normal way, so the
cockpit counts the case as completed. The backend puts the status of the loan request into the
details of the case, and the column "Status" of the case list shows it. The cockpit says
cancelled only when somebody of operations cancelled the process in the BPMS. The README of the
backend says more.

## Running it

You need Node.js 24, git and Docker. Docker builds the nginx image.

Clone the repository with its submodule, which brings the OpenAPI file of the backend:

```bash
git clone --recurse-submodules https://github.com/vanillabp-blueprints/cockpit-workflow-details-frontend-vue.git
```

If you cloned without it, `git submodule update --init` fetches the submodule later.

### Build and test

```bash
npm ci
npx playwright install chromium-headless-shell   # once, for the browser tests
npm run build
npm test
```

The scripts are the same as in `cockpit-user-task-frontend-vue`:

|             Script              |                                                                 What it does                                                                  |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                                                                     |
| `npm run fetch:openapi`         | copies the OpenAPI file of the backend into `build/openapi`                                                                                   |
| `npm run generate:client`       | generates the API client from it with `@hey-api/openapi-ts`                                                                                   |
| `npm run write:bc-build`        | writes the module id and the id of this build into `src/bc-build.js`, see [The names of the web components](#the-names-of-the-web-components) |
| `npm run typecheck`             | checks the types of the whole project, the templates of the components included, with `vue-tsc`                                               |
| `npm run build:micro-frontends` | builds the entries and `bc-manifest.json` into `dist/micro-frontends`                                                                         |
| `npm run check:entries`         | runs `bc-check-entries` of the kit: no bare import, no framework in a data entry, every file there                                            |
| `npm run build:jar`             | builds the UI jar `io.vanillabp.blueprint:cockpit-workflow-details-ui` and its pom into `dist`                                                |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run                                                     |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                                                                          |
| `npm run start`                 | the dev shell. Not there yet                                                                                                                  |

### With the backend during development

The page calls `/wm/loan-approval/api/cases/...`. During development the dev shell forwards
`/wm/loan-approval/` to the workflow module on your machine. The README of the backend says how
to start it, in "Running the backend for the development of the user interface". The dev shell
comes from a cockpit generated with the skill `business-cockpit-creator`, as the package
`@acme/bc-dev-shell-vue`. None publishes it yet, so `npm run start` only says so, and the
browser tests are the way to see the page.

## How it works

|                  File                   |                                                         Role                                                         |
|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                  | the names shared with the backend and the cockpit: module id, process id, entry, web components, keys of the details |
| `src/data/workflow-page.ts`             | data entry `WorkflowPage`: picks the page by `bpmnProcessId`                                                         |
| `src/data/workflow-list.ts`             | data entry `WorkflowList`: the columns `details.applicant` and `details.status` of the case list                     |
| `src/pages/loan-approval-page.ts`       | visual entry `LoanApprovalPage`: defines the four web components of the page from its own components                 |
| `src/pages/loan-approval-page/*.ce.vue` | the areas `all`, `body`, `information` and `actions`, and the parts they are made of                                 |
| `src/shared/case-page.ts`               | the composables of the areas: the case, the open user tasks, the messages between the areas                          |
| `src/shared/vue-element.ts`             | makes a Vue component a web component with `defineBcElement` of the kit                                              |
| `src/shared/api.ts`                     | `loanApproval`, the one instance of the generated `CasesApi` and `ResponseError`, which keeps the status             |
| `src/shared/style.ts`                   | the style of `:host` in each shadow root, which keeps long content from widening the page                            |
| `scripts/build-micro-frontends.mjs`     | compiles the `.vue` files, builds the entries with esbuild and writes `bc-manifest.json` with `createManifest`       |
| `test/browser/workflow-page.test.js`    | the browser tests in the host of the kit                                                                             |

### From the list to a withdrawn loan request

1. For the case list, the cockpit loads the data entry `WorkflowList` and calls
   `workflowListColumns`. The columns read `details.applicant` and `details.status`. The backend
   puts both there with its `@WorkflowDetailsProvider`.
2. Somebody opens a case. The cockpit loads the data entry `WorkflowPage` and calls
   `workflowPage` with the case. The function compares `bpmnProcessId` and returns
   `{ entry: 'LoanApprovalPage', element: { all, body, information, actions } }`. It imports no
   page, so this step loads no Vue.
3. The cockpit loads the visual entry `LoanApprovalPage`. Only now does the browser load Vue.
4. A cockpit with areas draws the web components of `body`, `information` and `actions`. A
   cockpit without areas draws the one of `all`. Each web component draws into its own shadow
   DOM.
5. `body` calls `workflow.getUserTasks(true, false)`: the open user tasks, also those of other
   people, so everybody who sees the case sees where it stands. The kit turns the call into a
   `bc-request` event, and the cockpit answers with its own list. The cockpit sets `workflow`
   again when the case changes, and `body` asks again.
6. `information` and `actions` load the case from `/wm/loan-approval/api/cases/{businessId}`. The
   business id of the case is the id of the loan request. They share one request.
7. "Withdraw loan request" posts to `/wm/loan-approval/api/cases/{businessId}/withdraw`. The
   module correlates the message which starts the event subprocess, and the case ends normally
   with the status `WITHDRAWN`. The page calls `showNotification`, and `actions` tells `body` and
   `information` with `sendTo` to load their data again. The button is enabled only while the
   status is `REQUESTED`.

### The names of the web components

All modules of a cockpit define their web components in the one registry of the page. A name
can be defined only once there, and the cockpit refuses a name which does not start with the
prefix of its module. So every name has three parts, built by `createElementNames` of the kit:

1. the prefix of the workflow module id, `loan-approval`,
2. the id of the build, the time of the build in milliseconds in base 36,
3. the BPMN process id and the part, `page` for the area `all` and the name of the area for the others.

`elementName.workflow(BPMN_PROCESS_ID, 'page')` in `src/loan-approval.ts` gives `loan-approval-<build id>-loan-approval--page`. A new build gets new names, so the
cockpit loads it while somebody has the cockpit open, without a reload of the page.

`npm run write:bc-build` (`scripts/write-bc-build.mjs`) writes `src/bc-build.js` before the type
check: `WORKFLOW_MODULE_ID` from `config.workflowModuleId` in `package.json`, and `BUILD_ID`.
`scripts/build-micro-frontends.mjs` passes the same time to `createManifest`, so the manifest and
the names speak of one build. The file is written for every build and not checked in, its types
are in `src/bc-build.d.ts`. The browser tests read the build id from it, and
`test/build-id.test.js` proves that two builds give different names.

The build id changes the names of the web components, but not the name of the file with Vue.
With one visual entry, esbuild would put Vue into the file of that entry. That file imports the
chunk with `src/bc-build.js`, so its name changes with every build, and the browser would load Vue
again. So `scripts/build-micro-frontends.mjs` adds one more entry which imports Vue only and
deletes it after the build. Two entries share Vue then, and esbuild puts it into a chunk of its
own, which imports nothing. After a new build the browser loads the entries and one small chunk
again, and takes Vue from its cache. `test/framework-chunk.test.js` builds twice and checks that
the name stays.

### Vue inside web components

Here the page belongs to the cockpit, and Vue only draws inside the web components of this
module. `src/shared/vue-element.ts` holds the code for that, the same file as in
`cockpit-user-task-frontend-vue`. When the cockpit draws a web component, `createApp` makes a
small Vue application for it and mounts it into the shadow root. When the cockpit removes the
web component, the application is unmounted. The four areas share the one copy of Vue in the
chunk of the framework.

The properties of the cockpit, such as `workflow` and `language`, go into the application with
`provide`, as one shallow ref. The components read them with `useBcElement()`, through
`useCasePage()` in `src/shared/case-page.ts`. When the cockpit sets `workflow` again, the ref gets
the new properties, and `useOpenUserTasks()` asks for the user tasks again.

The components are single-file components with `<script setup lang="ts">`, named `*.ce.vue`. A
small esbuild plugin in `scripts/build-micro-frontends.mjs` hands every `.vue` file to the Vue
compiler, `vue/compiler-sfc`, and puts the CSS of its `<style>` block into the list `styles` of
the component. `vue-element.ts` puts that list into the shadow root when the component is set up.
The build fails on a `<style>` block in a plain `.vue` file, because there it would have to go
into the `<head>` of the cockpit's page. `npm run typecheck` runs `vue-tsc`, which checks the
templates as well. The README of `cockpit-user-task-frontend-vue` explains all of this in more
detail.

### One visual entry, its own components

The visual entry `src/pages/loan-approval-page.ts` imports the four areas of the case page from
`src/pages/loan-approval-page/` and defines one web component for each. It imports nothing else
of the user interface of the module. So the cockpit loads the components of the case page when
it shows a case, and no others.

Do not let the visual entries import one shared list of names and components. esbuild would put
all components of the module into one chunk which every entry imports, and the cockpit would load
every part of the module whenever it shows one. A dev shell which draws every part of the module
needs such a list. It keeps the list for itself, and no visual entry imports it.

### Imports through path aliases

A file imports from another directory through a path alias, never with `../`. The aliases are
the `paths` of `tsconfig.json`:

|      Alias       |               Points to                |                           What it is                           |
|------------------|----------------------------------------|----------------------------------------------------------------|
| `@loan-approval` | `src/loan-approval.ts`                 | the names the module shares with its backend and the cockpit   |
| `@shared`        | `src/shared/index.ts`                  | what the other parts take from `src/shared`                    |
| `@gui-client`    | `src/shared/api.ts`                    | the client of the module API, and `ResponseError`              |
| `@generated`     | `src/generated/loan-approval/index.ts` | what the generator writes; only `src/shared/api.ts` imports it |

An import names what it wants, not where the file is. A file which moves changes one line of
`tsconfig.json` and no import. `src/shared/index.ts` exports what the other parts use, and
nothing else. Inside one directory a file imports its neighbour with `./`.

esbuild reads the same `paths`, because `scripts/build-micro-frontends.mjs` hands it
`tsconfig.json`. No plugin and no extra package is needed. The browser tests import no source file,
they load the build. So they need no aliases.

`npm run build` starts with `npm run check:imports`. `scripts/check-imports.mjs` fails the build
if a file in `src` imports with `../`, so the rule stays. The generated client is left out. The
project has no ESLint, which is why it is a script and not a lint rule.

### Test ids

|         Test id         |                                Element                                |
|-------------------------|-----------------------------------------------------------------------|
| `case-user-tasks`       | the list of open user tasks, with its heading (`body` or `all`)       |
| `case-user-task`        | one open user task, its title in the language of the user             |
| `case-no-user-tasks`    | the text shown when no user task is open                              |
| `case-user-tasks-error` | the message shown when the cockpit does not list the user tasks       |
| `case-loan-request`     | the id of the loan request (`information` or `all`)                   |
| `case-applicant`        | the applicant                                                         |
| `case-amount`           | the amount                                                            |
| `case-status`           | requested, approved or withdrawn                                      |
| `case-error`            | the message shown when the module API fails or does not know the case |
| `case-withdraw`         | the button "Withdraw loan request" (`actions` or `all`)               |
| `case-withdrawn`        | the text shown once the loan request is withdrawn                     |
| `case-withdraw-error`   | the message shown when the module refuses to withdraw                 |

### The browser tests

`npm test` starts a small web server and Chromium. The test page loads the build through
`createModuleHost` of `@vanillabp/bc-module-host`, as a cockpit would. There is no cockpit and no
backend. The tests answer the module API inside the browser, at the network level, and the host
answers `workflow.getUserTasks` with the user tasks a test gives it. Every case is tested twice:
in a host with the areas `body`, `information` and `actions`, and in a host which only knows
`all`. The tests check the data entries, that they load neither Vue nor the page, the open user
tasks and the data of the case, the withdrawal and what the other areas do after it, a case
which ended, a case the module does not know, a withdrawal the module refuses, and that words of
400 letters do not widen the page. They also check that the `<style>` blocks of the components
reach their shadow roots. The tests are the same as in `cockpit-workflow-details-frontend-react`,
apart from the names of the frameworks and that one check of the styles.

## Documentation

- [Loading the parts of a workflow module](https://github.com/vanillabp/business-cockpit/wiki/Loading-the-parts-of-a-workflow-module): entries, the manifest, areas and the event protocol
- [The module kit](https://github.com/vanillabp/business-cockpit-module-kit): `@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host`
- [`@hey-api/openapi-ts`](https://heyapi.dev/openapi-ts/get-started): the generator of the API client
- [Provide / inject](https://vuejs.org/guide/components/provide-inject.html): how the properties of the cockpit reach the components
- [`vue/compiler-sfc`](https://github.com/vuejs/core/tree/main/packages/compiler-sfc): the compiler of single-file components, which the build script calls
- [vue-tsc](https://github.com/vuejs/language-tools/tree/master/packages/tsc): the type checker for `.vue` files

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
