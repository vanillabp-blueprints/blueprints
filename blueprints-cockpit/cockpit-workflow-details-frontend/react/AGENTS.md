# cockpit-workflow-details-frontend-react

The case page of the workflow module `loan-approval` for a VanillaBP Business Cockpit, in React:
a data entry which picks the page of a case, the page with the open user tasks, the data of the
loan request and the button "Cancel case", and two columns of the case list. An npm project
without Java. It builds the micro-frontends, the UI jar and an nginx image. The backend with the
API and the application are separate projects. The OpenAPI file of the backend comes in through
the git submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This project is built like `cockpit-user-task-frontend-react`, which shows the forms of user
tasks. The scripts, the UI jar and the image are the same.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                      Meaning                                                       |
|----------------------------|--------------------------------------------------------------------------------------------------------------------|
| `loan-approval`            | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name |
| `loan_approval`            | BPMN process id, compared in both data entries                                                                     |
| `cockpit-workflow-details` | blueprint id: the names of the backend repository, the UI jar and the image                                        |

`loan-approval` and `loan_approval` are in `src/loan-approval.ts` and in `package.json`
(`config`). The process id must be the one of the BPMN file of the backend. If it is wrong, the
build does not fail. The data entry returns `undefined`, and the cockpit says that the case has
no page.

The web component names start with the workflow module id. Web components share one registry
per page. Two modules which define the same name break each other.

## Core files

|                 File                 |                                                                    Why it matters                                                                     |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`               | module id, process id, entry name, web component names and the keys of the details, in one place                                                      |
| `src/data/workflow-page.ts`          | data entry `WorkflowPage`. Plain TypeScript, it must never import React or the page                                                                   |
| `src/data/workflow-list.ts`          | data entry `WorkflowList`, the columns `details.applicant` and `details.outcome`                                                                      |
| `src/pages/loan-approval-page.tsx`   | visual entry `LoanApprovalPage`: the four web components (`all`, `body`, `information`, `actions`), `getUserTasks` and the messages between the areas |
| `src/shared/api.ts`                  | the client of the module API, base path `/wm/loan-approval` from the server of the OpenAPI file                                                       |
| `src/shared/style.ts`                | the styles, put into each shadow root. `overflow-wrap: anywhere` keeps long content from widening the page                                            |
| `scripts/build-micro-frontends.mjs`  | the entry list for esbuild, and `bc-manifest.json` from esbuild's outputs                                                                             |
| `test/browser/workflow-page.test.js` | the browser tests, with areas and with `all` only                                                                                                     |

## Boilerplate files

|              File              |                                      Purpose                                       |
|--------------------------------|------------------------------------------------------------------------------------|
| `package.json`                 | the scripts, and in `config` the module id, the jar coordinates and the image name |
| `tsconfig.json`                | type checking only; esbuild builds                                                 |
| `scripts/fetch-openapi.mjs`    | copies the OpenAPI file from the submodule, and its server as the client base URL  |
| `.gitmodules`                  | the submodule `backend`, the backend repository which owns the OpenAPI file        |
| `scripts/build-ui-jar.mjs`     | the UI jar and its pom                                                             |
| `scripts/build-image.mjs`      | the nginx image                                                                    |
| `scripts/start.mjs`            | placeholder for the dev shell                                                      |
| `src/shared/react-element.tsx` | a React component as a web component, with `defineBcElement` of the kit            |
| `src/shared/texts.ts`          | the texts in English and German                                                    |
| `docker/Dockerfile`            | the nginx image                                                                    |
| `docker/nginx.conf`            | serves `/loan-approval/`, the manifest without caching                             |
| `test/browser/page.js`         | the test page with `createModuleHost`, which answers `workflow.getUserTasks`       |
| `test/browser/server.js`       | serves the test page and `dist/micro-frontends` below `/wm/loan-approval/`         |

## Adding this blueprint to an existing project

1. If the workflow module has no frontend project yet, copy this one and replace the
   placeholders. If it has one, take the files below `src` and the scripts it lacks.
2. Each process with a case page gets a visual entry in `src/pages/` and a case in
   `src/data/workflow-page.ts`. Add the entry to the list in `scripts/build-micro-frontends.mjs`.
3. Read the open user tasks with `workflow.getUserTasks(activeOnly, limitList...)`. Do not call
   the API of the cockpit yourself. Read everything else from the module API, keyed by the
   `businessId` of the case.
4. A button which changes the case calls the module API, never the cockpit. Tell the other
   areas with `workflow.sendTo(area, message)` that they have to load their data again.
5. Each value for the case list needs a column in `src/data/workflow-list.ts`, and the backend
   has to put the value into `details` with its `@WorkflowDetailsProvider`.
6. Point the submodule in `.gitmodules` at your backend repository
   (`git submodule add <url> backend`), set `config.openapi` in `package.json` to the OpenAPI file
   inside it, and delete `config.openapiInMonorepo`. If your cockpit has a types library of its
   own, replace `@vanillabp/bc-module-kit` with it.
7. Keep the data entries free of React. `npm run check:entries` fails otherwise.

## Verifying

```bash
npm ci
npx playwright install chromium-headless-shell
npm run build
npm test
```

`npm run build` needs the OpenAPI file: the submodule `backend/` filled
(`git submodule update --init`), the backend directory next to this one in the monorepo, or
`OPENAPI_FILE`. `bc-check-entries` has to pass. It runs inside `npm run build`. Every browser test
has to pass, the ones with areas and the ones with `all` only.

Do not report success without having run this.
