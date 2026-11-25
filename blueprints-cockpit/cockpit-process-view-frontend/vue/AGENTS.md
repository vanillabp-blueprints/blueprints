# cockpit-process-view-frontend-vue

The case page of the workflow module `loan-approval` for a VanillaBP Business Cockpit, in Vue:
a data entry which picks the page of a case, and the page, which draws the process of the case
with bpmn-js and marks the steps the case passed and the step where it stands. An npm project
without Java. It builds the micro-frontends, the UI jar and an nginx image. The backend is
`bpmn-history-and-diagram`, whose endpoints `/diagram` and `/history` give the BPMN XML and the
history. Its OpenAPI file comes in through the git submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This project is built like `cockpit-workflow-details-frontend-vue`, which shows the data of a
case and its open user tasks. The scripts, the UI jar and the image are the same.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|      Placeholder       |                                                          Meaning                                                          |
|------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `loan-approval`        | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name        |
| `loan_approval`        | BPMN process id, compared in the data entry                                                                               |
| `LoanApproval`         | use case identifier, CamelCase: the start of class names, for example the interface `LoanApprovalApi` the build generates |
| `cockpit-process-view` | blueprint id: the names of the UI jar and the image                                                                       |

`loan-approval` and `loan_approval` are in `src/loan-approval.ts` and in `package.json`
(`config`). The process id must be the one of the BPMN file of the backend. If it is wrong, the
build does not fail. The data entry returns `undefined`, and the cockpit says that the case has
no page.

The name of the web component comes from `elementName` in `src/loan-approval.ts`, which is
`createElementNames` of the kit: the prefix of the workflow module id, the id of the build, the
BPMN process id and `process`, so `loan-approval-<build id>-loan-approval--process`. Never write a
name by hand. Web components share one registry per page, so two modules which define the same
name break each other, and the browser defines a name only once, so a new build needs new names
to load without a reload of the page. `npm run build` writes the module id and the build id into
`src/bc-build.js` first.

## Core files

|                   File                    |                                                                                 Why it matters                                                                                  |
|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                    | module id, process id, entry name and web component name, in one place                                                                                                          |
| `src/data/workflow-page.ts`               | data entry `WorkflowPage`. Plain TypeScript, it must never import Vue, the page or bpmn-js                                                                                      |
| `src/pages/loan-approval-process-page.ts` | visual entry `LoanApprovalProcessPage`: one web component for the area `all`, with `ProcessView.ce.vue`                                                                         |
| `src/pages/ProcessView.ce.vue`            | the page. It imports `./draw-process.ts` with `import()`, never statically, and draws again when the cockpit sets `workflow` again                                              |
| `src/pages/draw-process.ts`               | the only file which imports bpmn-js: the `Viewer`, its styles into the shadow root, the markers by the element ids of the history                                               |
| `src/shared/case-process.ts`              | loads `/diagram` and `/history` of the module API, keyed by the `businessId` of the case                                                                                        |
| `src/shared/api.ts`                       | `loanApproval`, the one instance of the generated `LoanApprovalApi`, and `ResponseError`                                                                                        |
| `openapi-ts.config.mjs`                   | the base URL `/wm` of the client, because the paths of the OpenAPI file start with `/loan-approval/api/`                                                                        |
| `scripts/build-micro-frontends.mjs`       | the entry list for esbuild, `.css` loaded as text, and `bc-manifest.json`; the plugin which compiles `.vue` files; a new import from a Vue package goes into `frameworkImports` |
| `test/browser/process-page.test.js`       | the browser tests, with areas and with `all` only                                                                                                                               |

## Boilerplate files

|               File                |                                                                         Purpose                                                                         |
|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `package.json`                    | the scripts, `bpmn-js` with an exact version, and in `config` the module id, the jar coordinates and the image name                                     |
| `tsconfig.json`                   | type checking with `vue-tsc`, templates included; esbuild builds; `paths` holds the aliases `@loan-approval`, `@shared`, `@gui-client` and `@generated` |
| `src/css.d.ts`                    | says that an import of a `.css` file gives a string                                                                                                     |
| `src/shared/index.ts`             | what the other directories import as `@shared`                                                                                                          |
| `src/shared/style.ts`             | the style every shadow root starts with; the diagram gets its height in `ProcessView.ce.vue`, or bpmn-js has nothing to fit the process into            |
| `scripts/check-imports.mjs`       | fails `npm run build` on an import with `../`; import through an alias instead                                                                          |
| `scripts/fetch-openapi.mjs`       | copies the OpenAPI file from the submodule and checks that it names a server                                                                            |
| `scripts/write-bc-build.mjs`      | writes `src/bc-build.js`, the module id from `package.json` and the build id, before the type check                                                     |
| `src/bc-build.d.ts`               | the types of `src/bc-build.js`, which is written for every build and not checked in                                                                     |
| `.gitmodules`                     | the submodule `backend`, the backend repository which owns the OpenAPI file                                                                             |
| `scripts/build-ui-jar.mjs`        | the UI jar and its pom                                                                                                                                  |
| `scripts/build-image.mjs`         | the nginx image                                                                                                                                         |
| `scripts/start.mjs`               | placeholder for the dev shell                                                                                                                           |
| `src/shared/vue-element.ts`       | a Vue component as a web component, with `defineBcElement` of the kit                                                                                   |
| `src/shared/texts.ts`             | the texts in English and German                                                                                                                         |
| `docker/Dockerfile`               | the nginx image                                                                                                                                         |
| `docker/nginx.conf`               | serves `/loan-approval/`, the manifest without caching, JavaScript and JSON with gzip                                                                   |
| `test/browser/page.js`            | the test page with `createModuleHost`                                                                                                                   |
| `test/browser/server.js`          | serves the test page and `dist/micro-frontends` below `/wm/loan-approval/`                                                                              |
| `test/browser/loan_approval.bpmn` | a copy of the model of the backend, which the tests answer `/diagram` with                                                                              |

## Adding this blueprint to an existing project

1. The workflow module needs the endpoints `/diagram` and `/history`. Take them over from
   `bpmn-history-and-diagram`: the two operations of its OpenAPI file, and the methods of its
   `Workflow`, `Service` and `ApiController` which call `ProcessService#getBpmnXml` and
   `#getWorkflowHistory`.
2. If the workflow module has no frontend project yet, copy this one and replace the placeholders.
   If it has one, take `src/pages/draw-process.ts`, `src/shared/case-process.ts`, `src/css.d.ts`,
   the `.css` loader of `scripts/build-micro-frontends.mjs` and the dependency `bpmn-js`.
3. If the module has a case page already, put the diagram into it, in `body` for a cockpit with
   areas. Import `./draw-process.ts` with `import()` only, so bpmn-js stays a chunk of its own.
   Otherwise add the visual entry of this project to the list in
   `scripts/build-micro-frontends.mjs` and a case to `src/data/workflow-page.ts`.
4. Give the box of the diagram a height. Keep the bpmn.io logo visible. Do not hide or cover
   `.bjs-powered-by`, the license of bpmn-js asks for it.
5. Point the submodule in `.gitmodules` at your backend repository
   (`git submodule add <url> backend`), set `config.openapi` in `package.json` to the OpenAPI file
   inside it, and delete `config.openapiInMonorepo`. If the server of your OpenAPI file is the
   cockpit path `/wm/<workflow-module-id>`, take `baseUrl` out of `openapi-ts.config.mjs`.
6. Keep the data entry free of Vue and bpmn-js. `npm run check:entries` fails on Vue, and the
   browser test "asking the data entry loads neither the page, nor Vue, nor bpmn-js" on the rest.

## Verifying

```bash
npm ci
npx playwright install chromium-headless-shell
npm run build
npm test
```

`npm run build` needs the OpenAPI file: the submodule `backend/` filled
(`git submodule update --init`), the backend directory in the monorepo, or `OPENAPI_FILE`.
`bc-check-entries` has to pass. It runs inside `npm run build`. Every browser test has to pass,
the ones with areas and the ones with `all` only, and `test/framework-chunk.test.js` with them.

Do not report success without having run this.
