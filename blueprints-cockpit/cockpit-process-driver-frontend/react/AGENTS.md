# cockpit-process-driver-frontend-react

The forms of the workflow module `loan-approval` for a VanillaBP Business Cockpit, in React, and
a dev shell whose page "Process driver" brings a new case to a chosen state and opens its user
task. An npm project without Java. It builds the micro-frontends, the dev shell, the UI jar and an
nginx image. The backend is the blueprint `module-process-driver`, with the workflow module and
the process driver. Its two OpenAPI files come in through the git submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This project is built like `cockpit-user-task-frontend-react`. The scripts for the micro-frontends,
the UI jar and the image are the same.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|       Placeholder        |                                                      Meaning                                                       |
|--------------------------|--------------------------------------------------------------------------------------------------------------------|
| `loan-approval`          | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name |
| `loan_approval`          | BPMN process id, compared in both data entries                                                                     |
| `assessRisk`             | task definition of the first user task                                                                             |
| `checkContract`          | task definition of the second user task                                                                            |
| `cockpit-process-driver` | blueprint id: the names of the UI jar and the image                                                                |
| `module-process-driver`  | the backend repository, in `.gitmodules`                                                                           |
| `process-driver.enabled` | the property which switches the driver on in the backend, named in the messages of the dev shell                   |

`loan-approval` and `loan_approval` are in `src/loan-approval.ts` and in `package.json`
(`config`). The task definitions must be the ones of the BPMN file of the backend. If one is
wrong, the build does not fail. The data entry returns `undefined`, and the cockpit says that the
task has no form.

The names of the web components come from `elementName` in `src/loan-approval.ts`, which is
`createElementNames` of the kit. A name is the prefix of the workflow module id, the id of the
build and the BPMN process id, the task definition and the part, `form` for the area `all` and the name of the area for the others, for example `loan-approval-<build id>-loan-approval--assess-risk--form`. Never write a name by hand. Web components
share one registry per page, so two modules which define the same name break each other, and
the browser defines a name only once, so a new build needs new names to load without a reload of
the page. `npm run build` writes the module id and the build id into `src/bc-build.js` first.

The states of the driver are not in this project. The page "Process driver" asks the backend for
them. Do not write them into the dev shell.
A state may end the workflow, like `CONTRACT_DECLINED`. Then `advance-to` names no user task,
and the page links only to the case. Tell such a state by the missing `userTaskId`, not by its
name.

## Core files

|                  File                   |                                                                                           Why it matters                                                                                            |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `dev-shell/src/process-driver-page.tsx` | the page "Process driver": loads the states, sends the test data to advance-to, links to the case and its user task                                                                                 |
| `dev-shell/src/cockpit.ts`              | the host of the module kit with the areas `body` and `actions`, and the three calls to the simulated cockpit                                                                                        |
| `dev-shell/src/user-task-page.tsx`      | reads the user task from the simulated cockpit and draws its form with `showUserTask`                                                                                                               |
| `dev-shell/src/element-names.ts`        | checks the names of the web components before the host draws them, with the note of a cockpit; keep it when the dev shell changes                                                                   |
| `scripts/start.mjs`                     | serves the dev shell and forwards `/wm/loan-approval/api/` and `/official-api/`                                                                                                                     |
| `src/shared/process-driver-api.ts`      | `processDriver`, the generated client of the driver. Only the dev shell imports it, through `@process-driver-client`                                                                                |
| `src/shared/api.ts`                     | `loanApproval`, the generated client of the module API, and `ResponseError`. The forms import it as `@gui-client` and complete their tasks with POST: `completeAssessRisk`, `completeCheckContract` |
| `src/loan-approval.ts`                  | module id, process id, task definitions, keys of the details, entry names and web component names                                                                                                   |
| `src/data/user-task-form.ts`            | data entry `UserTaskForm`. Plain TypeScript, it must never import React or a form                                                                                                                   |
| `src/shared/task-form.tsx`              | the web components `all`, `body` and `actions` of a form with one button, and the 409 of "Check contract"                                                                                           |
| `test/browser/dev-shell.test.js`        | the browser tests of the dev shell, among them: choose a state, create the case, open its user task                                                                                                 |

## Boilerplate files

|                  File                  |                                               Purpose                                               |
|----------------------------------------|-----------------------------------------------------------------------------------------------------|
| `package.json`                         | the scripts, and in `config` the module id, both OpenAPI files, the jar and the image name          |
| `tsconfig.json`                        | type checking only, and the path aliases, which esbuild reads as well                               |
| `openapi-ts.config.mjs`                | the options of `@hey-api/openapi-ts`, one job per OpenAPI file                                      |
| `scripts/fetch-openapi.mjs`            | copies both OpenAPI files from the submodule                                                        |
| `scripts/write-bc-build.mjs`           | writes `src/bc-build.js`, the module id from `package.json` and the build id, before the type check |
| `src/bc-build.d.ts`                    | the types of `src/bc-build.js`, which is written for every build and not checked in                 |
| `.gitmodules`                          | the submodule `backend`, the backend repository which owns the OpenAPI files                        |
| `scripts/build-micro-frontends.mjs`    | the entry list for esbuild, and `bc-manifest.json` from esbuild's outputs                           |
| `scripts/build-dev-shell.mjs`          | the dev shell into `dist/dev-shell`                                                                 |
| `scripts/build-ui-jar.mjs`             | the UI jar and its pom                                                                              |
| `scripts/build-image.mjs`              | the nginx image                                                                                     |
| `dev-shell/index.html`                 | the page of the dev shell                                                                           |
| `dev-shell/src/main.tsx`               | the three views of the dev shell                                                                    |
| `dev-shell/src/workflow-page.tsx`      | a case and its user tasks                                                                           |
| `src/data/user-task-list.ts`           | data entry `UserTaskList`, the columns `details.amount` and `details.creditScore`                   |
| `src/forms/assess-risk-form.tsx`       | visual entry `AssessRiskForm`                                                                       |
| `src/forms/check-contract-form.tsx`    | visual entry `CheckContractForm`                                                                    |
| `src/shared/response-error.ts`         | `ResponseError`, which keeps the status of an answer                                                |
| `scripts/check-imports.mjs`            | fails `npm run build` on an import with `../`; import through an alias instead                      |
| `src/shared/index.ts`                  | what the forms import as `@shared`                                                                  |
| `src/shared/react-element.tsx`         | a React component as a web component, with `defineBcElement` of the kit                             |
| `src/shared/style.ts`                  | the styles, put into each shadow root                                                               |
| `src/shared/texts.ts`                  | the texts in English and German                                                                     |
| `docker/Dockerfile`                    | the nginx image                                                                                     |
| `docker/nginx.conf`                    | serves `/loan-approval/`, the manifest without caching                                              |
| `test/browser/user-task-forms.test.js` | the browser tests of the forms, with areas and with `all` only                                      |
| `test/browser/page.js`                 | the test page of the forms with `createModuleHost`                                                  |
| `test/browser/server.js`               | serves the test pages, the dev shell and `dist/micro-frontends` below `/wm/loan-approval/`          |

The imports go through path aliases, never through `../`. `tsconfig.json` declares them, and
esbuild reads them from there: `@loan-approval`, `@shared`, `@gui-client` (`src/shared/api.ts`),
`@process-driver-client` (`src/shared/process-driver-api.ts`), and `@generated` and
`@generated-process-driver` for the generated clients. Inside one directory a file imports its
neighbour with `./`. `npm run check:imports`, the first step of `npm run build`, fails on `../`.

## Adding this blueprint to an existing project

1. The backend needs a process driver with a REST API, as `module-process-driver` shows it:
   `GET /loan-approval/api/process-driver/states` and
   `GET /loan-approval/api/process-driver/advance-to`, switched on by a Maven profile and a
   property. Without it, the page "Process driver" has nothing to call.
2. If the workflow module has no frontend project yet, copy this one and replace the
   placeholders. If it has one, take `dev-shell/`, `scripts/start.mjs`,
   `scripts/build-dev-shell.mjs`, `src/shared/process-driver-api.ts` and the second job in
   `openapi-ts.config.mjs`.
3. Add the OpenAPI file of the driver to `config.openapi` in `package.json`, next to the one of
   the module API. Point the submodule in `.gitmodules` at your backend repository
   (`git submodule add <url> backend`), and delete `config.openapiInMonorepo`.
4. Change the test data in `dev-shell/src/process-driver-page.tsx` to the parameters of your
   `advance-to`. Choose defaults with which every state can be reached.
5. Change `DRIVER_SWITCH` in the same file to the profile and the property of your backend.
6. If your cockpit generated a dev shell of its own, add the page "Process driver" there instead
   of keeping this dev shell. It needs the client of the driver and the two calls to the cockpit
   in `dev-shell/src/cockpit.ts`.
7. Keep the data entries free of React. `npm run check:entries` fails otherwise.

## Verifying

```bash
npm ci
npx playwright install chromium-headless-shell
npm run build
npm test
```

`npm run build` needs both OpenAPI files: the submodule `backend/` filled
(`git submodule update --init`), the backend directory in the monorepo, or
`OPENAPI_FILE_LOAN_APPROVAL` and `OPENAPI_FILE_PROCESS_DRIVER`. `bc-check-entries` has to pass. It
runs inside `npm run build`. Every browser test has to pass, those of the dev shell and those of
the forms.

Then try it against the backend: start its simulator and its application with the driver, run
`npm run start`, open `http://localhost:4200/#/process-driver`, choose a state and follow the link
to the user task.

Do not report success without having run this.
