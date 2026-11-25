![Header](./readme/vanillabp-headline.png)

# cockpit-process-view-frontend-vue

The page of a case for a VanillaBP Business Cockpit, written in Vue, which shows the process
of the case. bpmn-js draws the diagram, and markers show the steps the case passed and the step
where it stands. The cockpit loads the page at runtime from the workflow module `loan-approval`.
This project builds it with npm and nothing else, and it delivers it in two ways: as a UI jar for
a Java application, and as an nginx image.

The data comes from the API of
[`bpmn-history-and-diagram`](https://github.com/vanillabp-blueprints/blueprints/tree/main/blueprints-bpmn/bpmn-history-and-diagram):
the BPMN XML of the process, and the history of the case. That blueprint is the backend of this
one. Its README says how the module gets both from VanillaBP. A workflow module which shows its
process in the cockpit takes over its endpoints `/diagram` and `/history`.

The project is built like
[`cockpit-workflow-details-frontend-vue`](https://github.com/vanillabp-blueprints/cockpit-workflow-details-frontend-vue),
which shows the data of a case and its open user tasks, and like
[`cockpit-user-task-frontend-vue`](https://github.com/vanillabp-blueprints/cockpit-user-task-frontend-vue),
which shows the forms of user tasks. Their READMEs explain what this one only links:
micro-frontends and the two kinds of entries, why the parts come at runtime, the module kit as
the minimum, the OpenAPI file through a git submodule and the client generated from it, why npm
builds apart from Maven, and the UI jar and the nginx image. The same page in React is
[`cockpit-process-view-frontend-react`](https://github.com/vanillabp-blueprints/cockpit-process-view-frontend-react).
Both have the same tests and the same contract with the module kit.

## What this blueprint shows

The process `loan_approval` of `bpmn-history-and-diagram` retrieves a credit rating, waits for
the approval of a partner and then decides on the loan. The case page draws this process and
shows how far the case got. This project shows:

- one data entry `WorkflowPage` which picks the page of a case by its process,
- the page as a visual entry of its own, which the cockpit only loads when somebody opens a case,
- the diagram drawn with the `Viewer` of [bpmn-js](https://bpmn.io/toolkit/bpmn-js/), from the
  BPMN XML the module API gives,
- a marker on every step the case passed, and another one on the step where it stands, from the
  history the module API gives,
- the styles of bpmn-js inside the shadow root of the page, where they reach the diagram and
  nothing else,
- bpmn-js in a chunk of its own, which the browser loads only when the page draws a process,
- the bpmn.io logo in the corner of the diagram, which the license of bpmn-js asks for,
- the markers drawn again when the cockpit says that the case changed,
- browser tests which load the build the way a cockpit does, without a cockpit.

### One web component for every cockpit

The page offers one web component, for the area `all`. A cockpit with areas of its own, such as
`body`, `information` and `actions`, shows `all` when a page offers none of them. So the same
page works in both kinds of cockpit. A page which shows the process next to other data, such as
the case page of `cockpit-workflow-details-frontend-vue`, puts the diagram into its `body`.

### bpmn-js, and why a chunk of its own

In this build, bpmn-js 18.31 is about 220 kB of minified JavaScript, and most pages of a module
never show a process.
So the page component, `ProcessView.ce.vue`, imports `src/pages/draw-process.ts` with `import()`, and only that file imports
bpmn-js. esbuild then puts both into a chunk of its own. The data entry does not import the page,
and the page loads the chunk only when it draws. The chunk keeps its name from build to build, so
after a new build of the module the browser takes it from its cache.

bpmn-js comes from npm, under the [bpmn.io license](https://bpmn.io/license/). That license asks
to keep the bpmn.io logo visible, which the `Viewer` puts into the corner of the diagram. Nothing
here hides it, and a browser test checks that it is there.

## Running it

You need Node.js 24, git and Docker. Docker builds the nginx image.

Clone the repository with its submodule, which brings the OpenAPI file of the backend:

```bash
git clone --recurse-submodules https://github.com/vanillabp-blueprints/cockpit-process-view-frontend-vue.git
```

If you cloned without it, `git submodule update --init` fetches the submodule later.

### Build and test

```bash
npm ci
npx playwright install chromium-headless-shell   # once, for the browser tests
npm run build
npm test
```

The scripts are the same as in `cockpit-workflow-details-frontend-vue`:

|             Script              |                                                                 What it does                                                                  |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                                                                     |
| `npm run fetch:openapi`         | copies the OpenAPI file of the backend into `build/openapi`                                                                                   |
| `npm run generate:client`       | generates the API client from it with `@hey-api/openapi-ts`                                                                                   |
| `npm run write:bc-build`        | writes the module id and the id of this build into `src/bc-build.js`, see [The names of the web components](#the-names-of-the-web-components) |
| `npm run typecheck`             | checks the types of the whole project                                                                                                         |
| `npm run build:micro-frontends` | builds the entries and `bc-manifest.json` into `dist/micro-frontends`                                                                         |
| `npm run check:entries`         | runs `bc-check-entries` of the kit: no bare import, no framework in a data entry, every file there                                            |
| `npm run build:jar`             | builds the UI jar `io.vanillabp.blueprint:cockpit-process-view-ui` and its pom into `dist`                                                    |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run                                                     |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                                                                          |
| `npm run start`                 | the dev shell. Not there yet                                                                                                                  |

### With the backend during development

The page calls `/wm/loan-approval/api/{loanRequestId}/diagram` and `.../history`. During
development the dev shell forwards `/wm/loan-approval/` to the workflow module on your machine.
The README of `bpmn-history-and-diagram` says how to start it and how to bring a case to the step
where it waits. The dev shell comes from a cockpit generated with the skill
`business-cockpit-creator`, as the package `@acme/bc-dev-shell-vue`. None publishes it yet, so
`npm run start` only says so, and the browser tests are the way to see the page.

## How it works

|                   File                    |                                                       Role                                                       |
|-------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                    | the names shared with the backend and the cockpit: module id, process id, entry, web component                   |
| `src/data/workflow-page.ts`               | data entry `WorkflowPage`: picks the page by `bpmnProcessId`                                                     |
| `src/pages/loan-approval-process-page.ts` | visual entry `LoanApprovalProcessPage`: the web component of the page                                            |
| `src/pages/ProcessView.ce.vue`            | the page: heading, diagram and legend, and its styles in a `<style>` block                                       |
| `src/shared/vue-element.ts`               | a Vue component as a web component: an application each, the properties through `provide`, the styles            |
| `src/pages/draw-process.ts`               | draws the diagram with bpmn-js, puts its styles into the shadow root and sets the markers                        |
| `src/shared/case-process.ts`              | loads the BPMN XML and the history of a case, and tells a case the module does not know from a failure           |
| `src/shared/api.ts`                       | `loanApproval`, the one instance of the generated `LoanApprovalApi`, and `ResponseError`                         |
| `scripts/build-micro-frontends.mjs`       | builds the entries with esbuild, compiles `.vue` files, loads `.css` files as text and writes `bc-manifest.json` |
| `test/browser/process-page.test.js`       | the browser tests in the host of the kit                                                                         |

### From a click on a case to the markers

1. Somebody opens a case. The cockpit loads the data entry `WorkflowPage` and calls
   `workflowPage` with the case. The function compares `bpmnProcessId` and returns
   `{ entry: 'LoanApprovalProcessPage', element: 'loan-approval-<build id>-loan-approval--process' }`.
   It imports no page, so this step loads neither Vue nor bpmn-js.
2. The cockpit loads the visual entry `LoanApprovalProcessPage`. Now the browser loads Vue.
3. The cockpit draws the web component. `src/shared/vue-element.ts` mounts a small Vue
   application into its shadow root, with `src/pages/ProcessView.ce.vue`.
4. The component asks the module API for `/wm/loan-approval/api/{businessId}/diagram` and
   `.../history`. The business id of the case is the id of the loan request. At the same time it
   imports `src/pages/draw-process.ts`, and the browser loads the chunk with bpmn-js.
5. `drawProcess` puts the styles of bpmn-js into the shadow root, draws the BPMN XML with the
   `Viewer` and zooms it to the size of its box.
6. For every element of the history it sets a marker, a CSS class on the element in the diagram:
   `bc-passed` for an element which ended or was cancelled, `bc-current` for an element which
   started and has not ended. An element which runs more than once, in a loop, is current while
   one of its runs is. An id the diagram does not have is left out.
7. The cockpit sets `workflow` again when the case changes. A watcher of the component then
   loads the history again, removes the diagram and draws it again with the new markers.

A call activity starts a process of its own, with a history of its own. The page shows the main
process only. The OpenAPI file of the backend says how to ask for the history of a call
activity, with `historyContext`.

### The markers

bpmn-js writes the colours of a shape into its style attribute, so the CSS of a marker needs
`!important`. It colours the first child of `.djs-visual`, which is the outline of the shape: the
rectangle of a task, the circle of an event. Passed steps are green. The current step is amber,
with a wider outline. The legend below the diagram names both colours. Its styles are in the
`<style>` block of `ProcessView.ce.vue`. The styles of bpmn-js and of the markers come with
`src/pages/draw-process.ts`, because they belong to the chunk with bpmn-js.

### The names of the web components

All modules of a cockpit define their web components in the one registry of the page. A name
can be defined only once there, and the cockpit refuses a name which does not start with the
prefix of its module. So every name has three parts, built by `createElementNames` of the kit:

1. the prefix of the workflow module id, `loan-approval`,
2. the id of the build, the time of the build in milliseconds in base 36,
3. the BPMN process id and the part, here `process`.

`elementName.workflow(BPMN_PROCESS_ID, 'process')` in `src/loan-approval.ts` gives
`loan-approval-<build id>-loan-approval--process`. A new build gets new names, so the cockpit
loads it while somebody has the cockpit open, without a reload of the page.

`npm run write:bc-build` (`scripts/write-bc-build.mjs`) writes `src/bc-build.js` before the type
check: `WORKFLOW_MODULE_ID` from `config.workflowModuleId` in `package.json`, and `BUILD_ID`.
`scripts/build-micro-frontends.mjs` passes the same time to `createManifest`, so the manifest and
the names speak of one build. The file is written for every build and not checked in, its types
are in `src/bc-build.d.ts`. The browser tests read the build id from it, and
`test/build-id.test.js` proves that two builds give different names.

The build id changes the names of the web components, but not the names of the files with Vue
and with bpmn-js. With one visual entry, esbuild would put Vue into the file of that entry. That
file imports the chunk with `src/bc-build.js`, so its name changes with every build, and the
browser would load Vue again. So `scripts/build-micro-frontends.mjs` adds one more entry which
imports Vue only and deletes it after the build. Two entries share Vue then, and esbuild puts it
into a chunk of its own, which imports nothing. The chunk with bpmn-js imports nothing of this
module, so it keeps its name as well.
`test/framework-chunk.test.js` builds twice and checks that both names stay.

### Imports through path aliases

A file imports from another directory through a path alias, never with `../`. The aliases are
the `paths` of `tsconfig.json`:

|      Alias       |               Points to                |                           What it is                           |
|------------------|----------------------------------------|----------------------------------------------------------------|
| `@loan-approval` | `src/loan-approval.ts`                 | the names the module shares with its backend and the cockpit   |
| `@shared`        | `src/shared/index.ts`                  | what the other parts take from `src/shared`                    |
| `@gui-client`    | `src/shared/api.ts`                    | the client of the module API, and `ResponseError`              |
| `@generated`     | `src/generated/loan-approval/index.ts` | what the generator writes; only `src/shared/api.ts` imports it |

An import names what it wants, not where the file is. `src/shared/index.ts` exports what the
other parts use, and nothing else. Inside one directory a file imports its neighbour with `./`,
as `ProcessView.ce.vue` imports `./draw-process.ts`. `src/pages/draw-process.ts` takes only types from
`@gui-client`, so the chunk with bpmn-js does not carry the client.

`npm run build` starts with `npm run check:imports`. `scripts/check-imports.mjs` fails the build
if a file in `src` imports with `../`, so the rule stays.

### The base URL of the client

The OpenAPI file of `bpmn-history-and-diagram` names the application as its server,
`http://localhost:8080`, and its paths start with `/loan-approval/api/`. So a person can type each
URL into a browser. In the cockpit, `/wm/loan-approval/` goes to the `workflow-module-uri` of the
module, which ends with `/loan-approval`. So `openapi-ts.config.mjs` gives the client the base URL
`/wm`, and the page calls `/wm/loan-approval/api/...`. The browser talks to one origin only.

### Test ids

|      Test id      |                                Element                                |
|-------------------|-----------------------------------------------------------------------|
| `process-view`    | the page, with its heading                                            |
| `process-diagram` | the box bpmn-js draws into                                            |
| `process-loading` | the text shown until the diagram is drawn                             |
| `process-error`   | the message shown when the module API fails or does not know the case |

Inside the diagram, the tests find an element by its BPMN id, with the selector
`.djs-element[data-element-id="<id>"]` of bpmn-js, and read its marker from its classes.

### The browser tests

`npm test` starts a small web server and Chromium. The test page loads the build through
`createModuleHost` of `@vanillabp/bc-module-host`, as a cockpit would. There is no cockpit and no
backend. The tests answer the module API inside the browser, at the network level. The diagram is
`test/browser/loan_approval.bpmn`, a copy of the model of the backend, and each test chooses the
history. Every case is tested twice: in a host with the areas `body`, `information` and
`actions`, and in a host which only knows `all`.

The tests check that the data entry loads neither the page, nor Vue, nor bpmn-js. They check
that the page draws the diagram with the right markers on the right elements, that the styles of
bpmn-js work inside the shadow root, that the bpmn.io logo is visible, and that only the page
loads the chunk with bpmn-js. They also check that the markers move when the cockpit sets the
case again, and they check a case which ended, a case the module does not know and a module which
fails.

## Documentation

- [bpmn-js](https://bpmn.io/toolkit/bpmn-js/): the viewer which draws the diagram, and its [license](https://bpmn.io/license/)
- [Showing BPMN and execution history](https://github.com/vanillabp-blueprints/blueprints/tree/main/blueprints-bpmn/bpmn-history-and-diagram): the backend, and where the data comes from
- [Loading the parts of a workflow module](https://github.com/vanillabp/business-cockpit/wiki/Loading-the-parts-of-a-workflow-module): entries, the manifest, areas and the event protocol
- [The module kit](https://github.com/vanillabp/business-cockpit-module-kit): `@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host`
- [`@hey-api/openapi-ts`](https://heyapi.dev/openapi-ts/get-started): the generator of the API client

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
