![Header](./readme/vanillabp-headline.png)

# cockpit-workflow-details-frontend-react

The page of a case for a VanillaBP Business Cockpit, written in React, and the columns of the
case list. The cockpit loads them at runtime from the workflow module `loan-approval`. This
project builds them with npm and nothing else, and it delivers them in two ways: as a UI jar for
a Java application, and as an nginx image.

It is one of three parts of the blueprint `cockpit-workflow-details`:

|                   Part                    |                                   What it holds                                   |
|-------------------------------------------|-----------------------------------------------------------------------------------|
| `cockpit-workflow-details-backend`        | the workflow module and its API as an OpenAPI file, built with Maven              |
| `cockpit-workflow-details-frontend-react` | this project: the micro-frontends, the UI jar and the nginx image, built with npm |
| `cockpit-workflow-details-application`    | takes the workflow module and the UI jar and makes a runnable application of them |

The project is built like
[`cockpit-user-task-frontend-react`](https://github.com/vanillabp-blueprints/cockpit-user-task-frontend-react),
which shows the forms of user tasks. Its README explains what this one only links: micro-frontends
and the two kinds of entries, why the parts come at runtime, the module kit as the minimum, the
OpenAPI file through a git submodule, why npm builds apart from Maven, and the UI jar and the
nginx image.

## What this blueprint shows

The process `loan_approval` has one user task, "Review loan request", and an interrupting event
subprocess "Cancel case". The case page shows where a case stands and lets the user cancel it.
This project shows:

- one data entry `WorkflowPage` which picks the page of a case by its process,
- the page as a visual entry of its own, which the cockpit only loads when somebody opens a case,
- the page for a cockpit with areas (`body`, `information`, `actions`) and the same page for a
  cockpit without areas (`all`),
- the open user tasks of the case in `body`, from the cockpit through `workflow.getUserTasks(...)`,
  without a client of its own for the API of the cockpit,
- the data of the loan request in `information`, from the module API,
- the button "Cancel case" in `actions`, which calls the module API. The module ends the case
  through the event subprocess, and the cockpit learns about it from the module,
- areas which talk to each other: after "Cancel case", `actions` tells `body` and `information`
  to load their data again,
- long content which wraps instead of widening the page,
- the applicant and the outcome in two columns of the case list,
- browser tests which load the build the way a cockpit does, without a cockpit.

### The page of a case

The cockpit has a page for every case. Unlike the page of a user task, it has no header with
buttons of the cockpit. All of the page belongs to the workflow module. A cockpit with areas puts
`information` at the top of a column on the right and `actions` at its bottom, and the column
stays in place while a long `body` scrolls. A cockpit without areas shows the web component of
`all`, which arranges everything itself.

### Cancelled is a column

The cockpit marks a case as cancelled by its `endReason`. But a case which ends in the event
subprocess reached an end event, so the cockpit counts it as completed. Only the module knows
that it was cancelled. So the backend puts the outcome into the details of the case, and the
column "Outcome" of the case list shows it. The README of the backend says more.

## Running it

You need Node.js 24, git and Docker. Docker builds the nginx image.

Clone the repository with its submodule, which brings the OpenAPI file of the backend:

```bash
git clone --recurse-submodules https://github.com/vanillabp-blueprints/cockpit-workflow-details-frontend-react.git
```

If you cloned without it, `git submodule update --init` fetches the submodule later.

### Build and test

```bash
npm ci
npx playwright install chromium-headless-shell   # once, for the browser tests
npm run build
npm test
```

The scripts are the same as in `cockpit-user-task-frontend-react`:

|             Script              |                                            What it does                                            |
|---------------------------------|----------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                          |
| `npm run fetch:openapi`         | copies the OpenAPI file of the backend into `build/openapi`                                        |
| `npm run generate:client`       | generates the types of the API client from it                                                      |
| `npm run typecheck`             | checks the types of the whole project                                                              |
| `npm run build:micro-frontends` | builds the entries and `bc-manifest.json` into `dist/micro-frontends`                              |
| `npm run check:entries`         | runs `bc-check-entries` of the kit: no bare import, no framework in a data entry, every file there |
| `npm run build:jar`             | builds the UI jar `io.vanillabp.blueprint:cockpit-workflow-details-ui` and its pom into `dist`     |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run          |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                               |
| `npm run start`                 | the dev shell. Not there yet                                                                       |

### With the backend during development

The page calls `/wm/loan-approval/api/cases/...`. During development the dev shell forwards
`/wm/loan-approval/` to the workflow module on your machine. The README of the backend says how
to start it, in "Running the backend for the development of the user interface". The dev shell
comes from a cockpit generated with the skill `business-cockpit-creator`, as the package
`@acme/bc-dev-shell-react`. None publishes it yet, so `npm run start` only says so, and the
browser tests are the way to see the page.

## How it works

|                 File                 |                                                         Role                                                         |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`               | the names shared with the backend and the cockpit: module id, process id, entry, web components, keys of the details |
| `src/data/workflow-page.ts`          | data entry `WorkflowPage`: picks the page by `bpmnProcessId`                                                         |
| `src/data/workflow-list.ts`          | data entry `WorkflowList`: the columns `details.applicant` and `details.outcome` of the case list                    |
| `src/pages/loan-approval-page.tsx`   | visual entry `LoanApprovalPage`: the four web components of the page                                                 |
| `src/shared/react-element.tsx`       | makes a React component a web component with `defineBcElement` of the kit                                            |
| `src/shared/api.ts`                  | the client of the module API, typed by the generated `src/generated/loan-approval.ts`                                |
| `src/shared/style.ts`                | the styles in each shadow root, which keep long content from widening the page                                       |
| `scripts/build-micro-frontends.mjs`  | builds the entries with esbuild and writes `bc-manifest.json` with `createManifest` of the kit                       |
| `test/browser/workflow-page.test.js` | the browser tests in the host of the kit                                                                             |

### From the list to a cancelled case

1. For the case list, the cockpit loads the data entry `WorkflowList` and calls
   `workflowListColumns`. The columns read `details.applicant` and `details.outcome`. The backend
   puts both there with its `@WorkflowDetailsProvider`.
2. Somebody opens a case. The cockpit loads the data entry `WorkflowPage` and calls
   `workflowPage` with the case. The function compares `bpmnProcessId` and returns
   `{ entry: 'LoanApprovalPage', element: { all, body, information, actions } }`. It imports no
   page, so this step loads no React.
3. The cockpit loads the visual entry `LoanApprovalPage`. Only now does the browser load React.
4. A cockpit with areas draws the web components of `body`, `information` and `actions`. A
   cockpit without areas draws the one of `all`. Each web component draws into its own shadow
   DOM.
5. `body` calls `workflow.getUserTasks(true, false)`: the open user tasks, also those of other
   people, so everybody who sees the case sees where it stands. The kit turns the call into a
   `bc-request` event, and the cockpit answers with its own list. The cockpit sets `workflow`
   again when the case changes, and `body` asks again.
6. `information` and `actions` load the case from `/wm/loan-approval/api/cases/{businessId}`. The
   business id of the case is the id of the loan request. They share one request.
7. "Cancel case" posts to `/wm/loan-approval/api/cases/{businessId}/cancel`. The module
   correlates the message which starts the event subprocess, and the case ends. The page calls
   `showNotification`, and `actions` tells `body` and `information` with `sendTo` to load their
   data again. The button stays disabled for a case which has ended.

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
| `case-outcome`          | running, approved or cancelled                                        |
| `case-error`            | the message shown when the module API fails or does not know the case |
| `case-cancel`           | the button "Cancel case" (`actions` or `all`)                         |
| `case-cancelled`        | the text shown once the case is cancelled                             |
| `case-cancel-error`     | the message shown when the module refuses to cancel                   |

### The browser tests

`npm test` starts a small web server and Chromium. The test page loads the build through
`createModuleHost` of `@vanillabp/bc-module-host`, as a cockpit would. There is no cockpit and no
backend. The tests answer the module API inside the browser, at the network level, and the host
answers `workflow.getUserTasks` with the user tasks a test gives it. Every case is tested twice:
in a host with the areas `body`, `information` and `actions`, and in a host which only knows
`all`. The tests check the data entries, that they load neither React nor the page, the open user
tasks and the data of the case, the cancel and what the other areas do after it, a case which
ended, a case the module does not know, a cancel the module refuses, and that words of 400
letters do not widen the page.

## Documentation

- [Loading the parts of a workflow module](https://github.com/vanillabp/business-cockpit/blob/main/skills/business-cockpit-creator/references/module-loading.md): entries, the manifest, areas and the event protocol
- [The module kit](https://github.com/vanillabp/business-cockpit-module-kit): `@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host`
- [openapi-fetch](https://openapi-ts.dev/openapi-fetch/): the API client, and `openapi-typescript`, which generates its types

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
