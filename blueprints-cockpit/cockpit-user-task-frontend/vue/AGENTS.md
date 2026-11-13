# cockpit-user-task-frontend-vue

The forms of the workflow module `loan-approval` for a VanillaBP Business Cockpit, in Vue: two
user tasks, a data entry which picks the form, a column of the task list. An npm project without
Java. It builds the micro-frontends, the UI jar and an nginx image. The backend with the API and
the application are separate projects. The OpenAPI file of the backend comes in through the git
submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|     Placeholder     |                                                      Meaning                                                       |
|---------------------|--------------------------------------------------------------------------------------------------------------------|
| `loan-approval`     | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name |
| `loan_approval`     | BPMN process id, compared in both data entries                                                                     |
| `assessRisk`        | task definition of the first user task                                                                             |
| `checkDocuments`    | task definition of the second user task                                                                            |
| `cockpit-user-task` | blueprint id: the names of the backend repository, the UI jar and the image                                        |

`loan-approval` and `loan_approval` are in `src/loan-approval.ts` and in `package.json`
(`config`). The task definitions must be the ones of the BPMN file of the backend. If one is
wrong, the build does not fail. The data entry returns `undefined`, and the cockpit says that the
task has no form.

The web component names start with the workflow module id. Web components share one registry
per page. Two modules which define the same name break each other.

## Core files

|                  File                  |                                                  Why it matters                                                   |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                 | module id, process id, task definitions, entry names and web component names, in one place                        |
| `src/data/user-task-form.ts`           | data entry `UserTaskForm`. Plain TypeScript, it must never import Vue or a form                                   |
| `src/data/user-task-list.ts`           | data entry `UserTaskList`, the column `details.riskAssessment`                                                    |
| `src/forms/assess-risk-form.ts`        | visual entry `AssessRiskForm`. It exports nothing, evaluating it defines the web components                       |
| `src/forms/check-documents-form.ts`    | visual entry `CheckDocumentsForm`                                                                                 |
| `src/forms/*Information.ce.vue`        | the data each form shows to read                                                                                  |
| `src/shared/input-form.ts`             | the composables of the four areas of a form, and the messages between the areas                                   |
| `src/shared/input-form/*.ce.vue`       | the four areas (`all`, `body`, `information`, `actions`) and the parts they are made of                           |
| `src/shared/vue-element.ts`            | a Vue component as a web component: an application each, the properties through `provide`, the styles             |
| `src/shared/api.ts`                    | the client of the module API, base path `/wm/loan-approval` from the server of the OpenAPI file                   |
| `scripts/build-micro-frontends.mjs`    | the entry list for esbuild, the plugin which compiles `.vue` files, and `bc-manifest.json` from esbuild's outputs |
| `test/browser/user-task-forms.test.js` | the browser tests, with areas and with `all` only                                                                 |

## Boilerplate files

|            File             |                                      Purpose                                       |
|-----------------------------|------------------------------------------------------------------------------------|
| `package.json`              | the scripts, and in `config` the module id, the jar coordinates and the image name |
| `tsconfig.json`             | type checking with `vue-tsc`, templates included; esbuild builds                   |
| `scripts/fetch-openapi.mjs` | copies the OpenAPI file from the submodule, and its server as the client base URL  |
| `.gitmodules`               | the submodule `backend`, the backend repository which owns the OpenAPI file        |
| `scripts/build-ui-jar.mjs`  | the UI jar and its pom                                                             |
| `scripts/build-image.mjs`   | the nginx image                                                                    |
| `scripts/start.mjs`         | placeholder for the dev shell                                                      |
| `src/shared/texts.ts`       | the texts in English and German                                                    |
| `src/shared/style.ts`       | the style of `:host`, put into each shadow root before the styles of components    |
| `docker/Dockerfile`         | the nginx image                                                                    |
| `docker/nginx.conf`         | serves `/loan-approval/`, the manifest without caching                             |
| `test/browser/page.js`      | the test page with `createModuleHost`                                              |
| `test/browser/server.js`    | serves the test page and `dist/micro-frontends` below `/wm/loan-approval/`         |

## Adding this blueprint to an existing project

1. If the workflow module has no frontend project yet, copy this one and replace the
   placeholders. If it has one, take the files below `src` and the scripts it lacks.
2. Each user task gets a visual entry in `src/forms/` and a line in the table of
   `src/data/user-task-form.ts`. Add the entry to the list in
   `scripts/build-micro-frontends.mjs`. A form with one input field can use `defineInputForm`.
   A larger one writes its four web components itself, the same way.
3. Each value for the task list needs a column in `src/data/user-task-list.ts`, and the backend
   has to put the value into `details` with its `@UserTaskDetailsProvider`.
4. Point the submodule in `.gitmodules` at your backend repository
   (`git submodule add <url> backend`), set `config.openapi` in `package.json` to the OpenAPI file
   inside it, and delete `config.openapiInMonorepo`. If your cockpit has a types library of its
   own, replace `@vanillabp/bc-module-kit` with it.
5. Keep the data entries free of Vue. `npm run check:entries` fails otherwise.
6. Name every component `*.ce.vue`, as in a Vite project which builds web components. Only the
   `<style>` block of a `*.ce.vue` file goes into the shadow root. The build fails on a `<style>`
   block in a plain `.vue` file, because the usual tool chain would put it into the `<head>` of
   the cockpit's page, where it does not reach the shadow root. Write plain CSS, no `lang`.
7. Read the properties of the cockpit with `useBcElement()`, not as props of the component. They
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
`OPENAPI_FILE`. `bc-check-entries` has to pass. It runs inside `npm run build`. Every browser test has
to pass, the ones with areas and the ones with `all` only.

Do not report success without having run this.
