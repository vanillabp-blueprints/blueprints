# cockpit-workflow-details-frontend-vue

The case page of the workflow module `loan-approval` for a VanillaBP Business Cockpit, in Vue:
a data entry which picks the page of a case, the page with the open user tasks, the data of the
loan request and the button "Withdraw loan request", and two columns of the case list. An npm
project without Java. It builds the micro-frontends, the UI jar and an nginx image. The backend with the
API and the application are separate projects. The OpenAPI file of the backend comes in through
the git submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This project is built like `cockpit-user-task-frontend-vue`, which shows the forms of user
tasks. The scripts, the UI jar and the image are the same. `cockpit-workflow-details-frontend-react`
is the same case page in React, with the same tests.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|        Placeholder         |                                                                                 Meaning                                                                                 |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval`            | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name                                                      |
| `loan_approval`            | BPMN process id, compared in both data entries                                                                                                                          |
| `LoanApproval`             | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`            | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `cockpit-workflow-details` | blueprint id: the names of the backend repository, the UI jar and the image                                                                                             |

`loan-approval` and `loan_approval` are in `src/loan-approval.ts` and in `package.json`
(`config`). The process id must be the one of the BPMN file of the backend. If it is wrong, the
build does not fail. The data entry returns `undefined`, and the cockpit says that the case has
no page.

The names of the web components come from `elementName` in `src/loan-approval.ts`, which is
`createElementNames` of the kit. A name is the prefix of the workflow module id, the id of the
build and the BPMN process id and the part, `page` for the area `all` and the name of the area for the others, for example `loan-approval-<build id>-loan-approval--page`. Never write a name by hand. Web components
share one registry per page, so two modules which define the same name break each other, and
the browser defines a name only once, so a new build needs new names to load without a reload of
the page. `npm run build` writes the module id and the build id into `src/bc-build.js` first.

## Core files

|                  File                   |                                                                                 Why it matters                                                                                  |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                  | module id, process id, entry name, web component names and the keys of the details, in one place                                                                                |
| `src/data/workflow-page.ts`             | data entry `WorkflowPage`. Plain TypeScript, it must never import Vue or the page                                                                                               |
| `src/data/workflow-list.ts`             | data entry `WorkflowList`, the columns `details.applicant` and `details.status`                                                                                                 |
| `src/pages/loan-approval-page.ts`       | visual entry `LoanApprovalPage`: defines the four web components (`all`, `body`, `information`, `actions`) from its own components and nothing else                             |
| `src/pages/loan-approval-page/*.ce.vue` | the four areas and the parts they are made of: the open user tasks, the data of the loan request, the button                                                                    |
| `src/shared/case-page.ts`               | the composables of the areas: the case loaded once for all areas, `getUserTasks`, and the messages between the areas                                                            |
| `src/shared/vue-element.ts`             | a Vue component as a web component: an application each, the properties through `provide`, the styles                                                                           |
| `src/shared/api.ts`                     | `loanApproval`, the one instance of the generated `CasesApi` and `ResponseError`; base URL `/wm/loan-approval` from the OpenAPI file                                            |
| `src/shared/style.ts`                   | the style of `:host`, put into each shadow root before the styles of components. `overflow-wrap: anywhere` keeps long content from widening the page                            |
| `scripts/build-micro-frontends.mjs`     | the entry list for esbuild, the plugin which compiles `.vue` files, and `bc-manifest.json` from esbuild's outputs; a new import from a Vue package goes into `frameworkImports` |
| `test/browser/workflow-page.test.js`    | the browser tests, with areas and with `all` only                                                                                                                               |

## Boilerplate files

|             File             |                                                                         Purpose                                                                         |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `package.json`               | the scripts, and in `config` the module id, the jar coordinates and the image name                                                                      |
| `tsconfig.json`              | type checking with `vue-tsc`, templates included; esbuild builds; `paths` holds the aliases `@loan-approval`, `@shared`, `@gui-client` and `@generated` |
| `src/shared/index.ts`        | what the other directories import as `@shared`                                                                                                          |
| `scripts/check-imports.mjs`  | fails `npm run build` on an import with `../`; import through an alias instead                                                                          |
| `scripts/fetch-openapi.mjs`  | copies the OpenAPI file from the submodule and checks that it names a server                                                                            |
| `scripts/write-bc-build.mjs` | writes `src/bc-build.js`, the module id from `package.json` and the build id, before the type check                                                     |
| `src/bc-build.d.ts`          | the types of `src/bc-build.js`, which is written for every build and not checked in                                                                     |
| `openapi-ts.config.mjs`      | the options of `@hey-api/openapi-ts`, the generator of the API client                                                                                   |
| `.gitmodules`                | the submodule `backend`, the backend repository which owns the OpenAPI file                                                                             |
| `scripts/build-ui-jar.mjs`   | the UI jar and its pom                                                                                                                                  |
| `scripts/build-image.mjs`    | the nginx image                                                                                                                                         |
| `scripts/start.mjs`          | placeholder for the dev shell                                                                                                                           |
| `src/shared/texts.ts`        | the texts in English and German                                                                                                                         |
| `docker/Dockerfile`          | the nginx image                                                                                                                                         |
| `docker/nginx.conf`          | serves `/loan-approval/`, the manifest without caching, JavaScript and JSON with gzip                                                                   |
| `test/browser/page.js`       | the test page with `createModuleHost`, which answers `workflow.getUserTasks`                                                                            |
| `test/browser/server.js`     | serves the test page and `dist/micro-frontends` below `/wm/loan-approval/`                                                                              |

## Adding this blueprint to an existing project

1. If the workflow module has no frontend project yet, copy this one and replace the placeholders.
   If it has one, take the files below `src`, the scripts it lacks and `openapi-ts.config.mjs`.
2. Each process with a case page gets a visual entry in `src/pages/` and a case in
   `src/data/workflow-page.ts`. Add the entry to the list in `scripts/build-micro-frontends.mjs`.
   The visual entry imports its own components and calls `defineVueElement` for each web
   component. It never imports a list of all components of the module, because then every
   entry would share one chunk with all of them, and the cockpit would load every part whenever
   it shows one. A dev shell which draws every part keeps such a list for itself.
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
7. Keep the data entries free of Vue. `npm run check:entries` fails otherwise.
8. Name every component `*.ce.vue`, as in a Vite project which builds web components. Only the
   `<style>` block of a `*.ce.vue` file goes into the shadow root. The build fails on a `<style>`
   block in a plain `.vue` file, because the usual tool chain would put it into the `<head>` of
   the cockpit's page, where it does not reach the shadow root. Write plain CSS, no `lang`.
9. Read the properties of the cockpit with `useBcElement()`, not as props of the component. They
   reach the application through `provide`.

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
