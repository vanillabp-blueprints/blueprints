# cockpit-user-task-frontend-react

The forms of the workflow module `loan-approval` for a VanillaBP Business Cockpit, in React: two
user tasks, a data entry which picks the form, a column of the task list. An npm project without
Java. It builds the micro-frontends, the UI jar and an nginx image. The backend with the API and
the application are separate projects.

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
| `cockpit-user-task` | blueprint id: the names of the API jar, the UI jar and the image                                                   |

`loan-approval` and `loan_approval` are in `src/loan-approval.ts` and in `package.json`
(`config`). The task definitions must be the ones of the BPMN file of the backend. If one is
wrong, the build does not fail. The data entry returns `undefined`, and the cockpit says that the
task has no form.

The web component names start with the workflow module id. Web components share one registry
per page. Two modules which define the same name break each other.

## Core files

|                  File                  |                                              Why it matters                                               |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                 | module id, process id, task definitions, entry names and web component names, in one place                |
| `src/data/user-task-form.ts`           | data entry `UserTaskForm`. Plain TypeScript, it must never import React or a form                         |
| `src/data/user-task-list.ts`           | data entry `UserTaskList`, the column `details.riskAssessment`                                            |
| `src/forms/assess-risk-form.tsx`       | visual entry `AssessRiskForm`. It exports nothing, evaluating it defines the web components               |
| `src/forms/check-documents-form.tsx`   | visual entry `CheckDocumentsForm`                                                                         |
| `src/shared/input-form.tsx`            | the four web components of a form (`all`, `body`, `information`, `actions`) and the messages between them |
| `src/shared/api.ts`                    | the client of the module API, base path `/wm/loan-approval`                                               |
| `scripts/build-micro-frontends.mjs`    | the entry list for esbuild, and `bc-manifest.json` from esbuild's outputs                                 |
| `test/browser/user-task-forms.test.js` | the browser tests, with areas and with `all` only                                                         |

## Boilerplate files

|              File              |                                      Purpose                                       |
|--------------------------------|------------------------------------------------------------------------------------|
| `package.json`                 | the scripts, and in `config` the module id, the jar coordinates and the image name |
| `tsconfig.json`                | type checking only; esbuild builds                                                 |
| `scripts/fetch-openapi.mjs`    | takes the OpenAPI file out of the API jar                                          |
| `scripts/build-ui-jar.mjs`     | the UI jar and its pom                                                             |
| `scripts/build-image.mjs`      | the nginx image                                                                    |
| `scripts/start.mjs`            | placeholder for the dev shell                                                      |
| `src/shared/react-element.tsx` | a React component as a web component, with `defineBcElement` of the kit            |
| `src/shared/texts.ts`          | the texts in English and German                                                    |
| `src/shared/style.ts`          | the styles, put into each shadow root                                              |
| `docker/Dockerfile`            | the nginx image                                                                    |
| `docker/nginx.conf`            | serves `/loan-approval/`, the manifest without caching                             |
| `test/browser/page.js`         | the test page with `createModuleHost`                                              |
| `test/browser/server.js`       | serves the test page and `dist/micro-frontends` below `/wm/loan-approval/`         |

## Adding this blueprint to an existing project

1. If the workflow module has no frontend project yet, copy this one and replace the
   placeholders. If it has one, take the files below `src` and the scripts it lacks.
2. Each user task gets a visual entry in `src/forms/` and a line in the table of
   `src/data/user-task-form.ts`. Add the entry to the list in
   `scripts/build-micro-frontends.mjs`. A form with one input field can use `defineInputForm`.
   A larger one writes its four web components itself, the same way.
3. Each value for the task list needs a column in `src/data/user-task-list.ts`, and the backend
   has to put the value into `details` with its `@UserTaskDetailsProvider`.
4. Point `config.apiJar` in `package.json` at the API jar of your backend. If your cockpit has a
   types library of its own, replace `@vanillabp/bc-module-kit` with it.
5. Keep the data entries free of React. `npm run check:entries` fails otherwise.

## Verifying

```bash
npm ci
npx playwright install chromium-headless-shell
npm run build
npm test
```

`npm run build` needs the API jar, from `~/.m2` after `mvn install` of the backend, or from
`API_JAR`. `bc-check-entries` has to pass. It runs inside `npm run build`. Every browser test has
to pass, the ones with areas and the ones with `all` only.

Do not report success without having run this.
