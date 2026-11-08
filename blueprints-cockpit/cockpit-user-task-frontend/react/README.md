![Header](./readme/vanillabp-headline.png)

# cockpit-user-task-frontend-react

The forms of two user tasks for a VanillaBP Business Cockpit, written in React. The cockpit
loads them at runtime from the workflow module `loan-approval`. This project builds them with
npm and nothing else, and it delivers them in two ways: as a UI jar for a Java application, and
as an nginx image.

It is one of three parts of the blueprint `cockpit-user-task`:

|                Part                |                                   What it holds                                   |
|------------------------------------|-----------------------------------------------------------------------------------|
| `cockpit-user-task-backend`        | the workflow module and its API as an OpenAPI file in a jar, built with Maven     |
| `cockpit-user-task-frontend-react` | this project: the micro-frontends, the UI jar and the nginx image, built with npm |
| `cockpit-user-task-application`    | takes the workflow module and the UI jar and makes a runnable application of them |

There are two more frontends with the same content, one in Angular and one in Vue. All three
build a UI jar with the same Maven coordinates and use the same test ids.

## What this blueprint shows

The process `loan_approval` has two user tasks, one after the other:

1. "Assess risk" (`assessRisk`): somebody reads the loan request and writes a risk assessment.
2. "Check documents" (`checkDocuments`): somebody reads that risk assessment, which they cannot
   change, and writes a note on the documents.

This project shows:

- how a workflow module brings its own forms into a cockpit it does not know, as standalone ES
  modules loaded at runtime,
- one data entry `UserTaskForm` which picks the form by process and task definition,
- each form as a visual entry of its own, which the cockpit only loads when somebody opens it,
- each visible part as a web component with its own shadow DOM, so the styles of the cockpit and
  of the module never mix,
- a form for a cockpit with areas (`body`, `information`, `actions`) and the same form for a
  cockpit without areas (`all`),
- areas which talk to each other: "Complete" in `actions` is only enabled once the field in
  `body` is filled,
- the functions of the cockpit, such as a notification, called through DOM events,
- every call to the module going through the cockpit, to `/wm/loan-approval/`,
- the risk assessment in a column of the task list of the cockpit,
- browser tests which load the build the way a cockpit does, without a cockpit.

### Why the forms come at runtime

The cockpit shows the user tasks of every workflow module in one list. Somebody opens a task,
and the cockpit shows its page: the title, who has it, the due date, the buttons to claim it or to
set a follow-up date. Only the form in the middle belongs to the workflow module. So the module
gives the cockpit just that part, and the cockpit loads it when somebody opens the task. The
same holds for the columns of the task list. The module says which values of its tasks the list
shows, and the cockpit asks it at runtime.

The other way is a page which comes from the workflow module as a whole. Then each module has to
build claiming, due dates and follow-ups itself, and each one looks a little different. The
columns of the list have to be fixed for all modules, because nobody can ask a module for its
columns. That works for one module, and it gets harder with each one more.

### Micro-frontends

For the people who use the cockpit, the parts a module brings are **micro-frontends**: small
frontends of their own, which a larger one loads. In the code and in the contract they are called
entries, and `bc-manifest.json` lists them. There are two kinds:

- A **data entry** is plain JavaScript without a framework. The cockpit asks it questions, for
  example "which form does this user task have?". This project has `UserTaskForm` and
  `UserTaskList`.
- A **visual entry** defines web components. The cockpit loads it only when it draws one of them.
  This project has `AssessRiskForm` and `CheckDocumentsForm`.

### The module kit is only the minimum

The project builds against `@vanillabp/bc-module-kit`. The kit holds the minimum every cockpit
understands: the names of the entries, the manifest, the event protocol and `defineBcElement`.
A cockpit generated with the skill `business-cockpit-user-interface` brings its own types library
with the same minimum. It may offer more, for example more areas or more entries. If you start
from this blueprint, replace the kit with the types library of your cockpit later. The code
stays the same where it uses the minimum.

## Running it

You need Node.js 24 and Docker. Docker runs the Verdaccio registry for the module kit and builds
the nginx image.

### The module kit from a local registry

The packages `@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host` are not on npmjs.com
yet. Until they are, they come from a local [Verdaccio](https://verdaccio.org) registry. The
file `.npmrc` sends the scope `@vanillabp` there, and everything else still comes from
npmjs.com. Start the registry, then publish the kit into it from a clone of
[`business-cockpit-module-kit`](https://github.com/vanillabp/business-cockpit-module-kit):

```bash
docker run -d --name verdaccio -p 4873:4873 \
  -v "$PWD/verdaccio/config.yaml:/verdaccio/conf/config.yaml:ro" verdaccio/verdaccio:6

cd ../business-cockpit-module-kit
for package in bc-module-kit bc-module-host; do
  npm publish --workspace packages/$package --registry http://localhost:4873 \
    --//localhost:4873/:_authToken=local --tag snapshot
done
```

`verdaccio/config.yaml` lets anybody publish, so keep the registry on your own machine. The
token is a dummy, because npm does not publish without one. `package-lock.json` holds the
checksums of the kit at the commit the CI uses (`KIT_REF` in `.github/workflows/frontend.yaml`
of the monorepo). If you publish another commit, `npm ci` fails with `EINTEGRITY`. Then run
`npm install` to take the new checksums.

Once the kit is on npmjs.com, delete `.npmrc` and the folder `verdaccio`.

### Build and test

```bash
npm ci
npx playwright install chromium-headless-shell   # once, for the browser tests
npm run build
npm test
```

|             Script              |                                            What it does                                            |
|---------------------------------|----------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                          |
| `npm run fetch:openapi`         | takes the OpenAPI file out of the API jar of the backend                                           |
| `npm run generate:client`       | generates the types of the API client from it                                                      |
| `npm run typecheck`             | checks the types of the whole project                                                              |
| `npm run build:micro-frontends` | builds the entries and `bc-manifest.json` into `dist/micro-frontends`                              |
| `npm run check:entries`         | runs `bc-check-entries` of the kit: no bare import, no framework in a data entry, every file there |
| `npm run build:jar`             | builds the UI jar and its pom into `dist`                                                          |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run          |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                               |
| `npm run start`                 | the dev shell. Not there yet, see below                                                            |

### The API jar

The forms call the API of the workflow module. The backend describes that API in an OpenAPI
file and ships it in the jar `io.vanillabp.blueprint:cockpit-user-task-api`. This project
generates its typed client from that file, so a changed API breaks the build here and not the
form in the browser. `scripts/fetch-openapi.mjs` looks for the jar in this order:

1. `OPENAPI_FILE`, the path of an OpenAPI file, for example a draft of a new API,
2. `API_JAR`, the path of the jar, for example one a CI job built before,
3. `MAVEN_REPOSITORY_URL`, a Maven repository such as GitHub Packages, with `MAVEN_USERNAME`
   and `MAVEN_PASSWORD` if it needs a login,
4. the local Maven repository `~/.m2/repository`, which `mvn install` of the backend fills,
5. `.openapi/loan-approval.yaml`, a copy you put there by hand. Git ignores it, and the build
   warns when it uses it.

The jar is a zip file, so reading it needs no Java.

### With the backend during development

The forms call `/wm/loan-approval/api/...`. In production the cockpit forwards `/wm/loan-approval/`
to the workflow module. During development the dev shell does it, and the workflow module runs
on your machine. You need no application for that. The backend starts the module the way its
test does, with an embedded BPMS and a database in memory. Its README says how, in "Running the
backend for the development of the user interface". In short:

1. Build the backend once, without its tests.
2. Start the dev shell simulator, which stands in for the server of the cockpit. It listens on
   port 8079.
3. Start the workflow module in a second terminal. It reports to the simulator and answers on
   port 8080.

Start a loan approval at `http://localhost:8080/loan-approval/api/loan-requests/start?amount=5000`.
The dev shell forwards `/wm/loan-approval/` to `http://localhost:8080/loan-approval/`, the same
way the cockpit does.

### The dev shell (not there yet)

Loading at runtime breaks hot module replacement: the cockpit loads a built file, not the
sources. The dev shell is a small cockpit for development. It shows the forms of this project
from its sources, forwards `/wm/` to the backend, and simulates users, so you can try the forms
with different permissions.

The dev shell comes from a cockpit generated with the skill `business-cockpit-user-interface`.
Such a cockpit publishes it as the package `@acme/bc-dev-shell-react`, with the scope of the
organisation instead of `@acme`. None of them publishes it yet. Until one does, `npm run start`
only says so, and the browser tests are the way to see the forms.

## How it works

|                  File                  |                                                Role                                                 |
|----------------------------------------|-----------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                 | the names shared with the backend and the cockpit: module id, process id, task definitions, entries |
| `src/data/user-task-form.ts`           | data entry `UserTaskForm`: picks the form by `bpmnProcessId` and `taskDefinition`                   |
| `src/data/user-task-list.ts`           | data entry `UserTaskList`: the column `details.riskAssessment` of the task list                     |
| `src/forms/assess-risk-form.tsx`       | visual entry `AssessRiskForm`: the form of "Assess risk"                                            |
| `src/forms/check-documents-form.tsx`   | visual entry `CheckDocumentsForm`: the form of "Check documents"                                    |
| `src/shared/input-form.tsx`            | the four web components of a form with one input field, and the messages between its areas          |
| `src/shared/react-element.tsx`         | makes a React component a web component with `defineBcElement` of the kit                           |
| `src/shared/api.ts`                    | the client of the module API, typed by the generated `src/generated/loan-approval.ts`               |
| `scripts/build-micro-frontends.mjs`    | builds the entries with esbuild and writes `bc-manifest.json` with `createManifest` of the kit      |
| `scripts/build-ui-jar.mjs`             | builds the UI jar with a zip library                                                                |
| `docker/Dockerfile`                    | the nginx image                                                                                     |
| `test/browser/user-task-forms.test.js` | the browser tests in the host of the kit                                                            |

### From the list to a completed task

1. The cockpit loads `/wm/loan-approval/bc-manifest.json`. The backend configures
   `uiUriType: ES_MODULES` and `uiUriPath: /bc-manifest.json`, so the cockpit knows where it is.
2. For the task list, the cockpit loads the data entry `UserTaskList` and calls
   `userTaskListColumns`. The column reads `details.riskAssessment`. The backend puts the risk
   assessment there with its `@UserTaskDetailsProvider`, once "Assess risk" is completed.
3. Somebody opens a task. The cockpit loads the data entry `UserTaskForm` and calls
   `userTaskForm` with the task. The function compares `bpmnProcessId` and `taskDefinition` and
   returns the visual entry and the names of its web components, for example
   `{ entry: 'AssessRiskForm', element: { all, body, information, actions } }`. It imports no
   form, so this step loads no React.
4. The cockpit loads the visual entry `AssessRiskForm`. Only now does the browser load React, in a
   chunk the two forms share. The other form is never loaded.
5. A cockpit with the areas `body`, `information` and `actions` draws those three web
   components. A cockpit without areas draws the one of `all`. Each web component draws into its
   own shadow DOM.
6. The form loads its data from `/wm/loan-approval/api/user-tasks/{userTaskId}/assess-risk`. The
   cockpit forwards the call to the workflow module. It never sees the data of the form.
7. In a cockpit with areas, `body` sends every change of the field to `actions` with
   `sendTo('actions', ...)`, and `actions` gets it with `receiveMessage`. "Complete" is enabled
   once the field is filled. `actions` may start listening after `body` sent its first value, so
   it asks `body` for the value once it listens.
8. "Complete" posts the risk assessment to the same path. The workflow module completes the task,
   and that tells the cockpit. The form calls `showNotification`, which the kit turns into a
   `bc-request` event on the web component. The cockpit shows the notification and answers with
   a `bc-response` event.

The module does not keep a draft. Only the result of a completed user task goes into the
workflow aggregate. How to keep a draft is the subject of another blueprint.

### Test ids

These `data-testid`s are the same in the React, the Angular and the Vue frontend, so the same
end-to-end tests run against all three. They are inside the shadow DOM of the web components.
Playwright finds them there with `getByTestId`.

|            Test id             |                             Element                              |
|--------------------------------|------------------------------------------------------------------|
| `assess-risk-form`             | the field of "Assess risk", with its label (`body` or `all`)     |
| `assess-risk-input`            | the text field for the risk assessment                           |
| `assess-risk-loan-request`     | the id of the loan request (`information` or `all`)              |
| `assess-risk-amount`           | the amount of the loan request (`information` or `all`)          |
| `assess-risk-complete`         | the button "Complete" (`actions` or `all`)                       |
| `assess-risk-done`             | the text shown once the task is completed                        |
| `assess-risk-error`            | the message shown when the module API fails or the task is gone  |
| `check-documents-form`         | the field of "Check documents", with its label (`body` or `all`) |
| `check-documents-input`        | the text field for the note on the documents                     |
| `check-documents-loan-request` | the id of the loan request (`information` or `all`)              |
| `check-documents-risk`         | the risk assessment of the task before, read-only                |
| `check-documents-complete`     | the button "Complete" (`actions` or `all`)                       |
| `check-documents-done`         | the text shown once the task is completed                        |
| `check-documents-error`        | the message shown when the module API fails or the task is gone  |

### The browser tests

`npm test` starts a small web server and Chromium. The test page loads the build through
`createModuleHost` of `@vanillabp/bc-module-host`, as a cockpit would. There is no cockpit and no
backend. The tests answer the module API inside the browser, at the network level, so the forms
run unchanged. Every form is tested twice: in a host with the areas `body`, `information` and
`actions`, and in a host which only knows `all`. The tests also check that the data entries load
neither React nor a form, and that opening one form does not load the other.

### Three repositories, and the order of their releases

Many companies give their build agents one Docker image with Java and Maven and another one with
Node. A repository where Maven and npm build together is hard to have there. So a workflow
module with a user interface has three parts, each built with one tool: the backend with Maven,
this frontend with npm, and the application with Maven again.

The frontend needs only the API, and the API is in the backend. Only the application needs both.
So there is no cycle, and a release goes in this order:

1. the backend, which releases the API jar,
2. the frontend, which reads the released API jar,
3. the application, which takes the released workflow module and the released UI jar.

After a release of the backend, Renovate proposes the new version to the frontend and to the
application as a pull request. More repositories to release is the price for build agents with
one tool each.

### Why npm builds apart from Maven

A Maven plugin which downloads Node and runs npm, like `frontend-maven-plugin`, puts a second tool
chain into the Maven build. Many companies see that as a security risk. And a Maven release
should stay an ordinary Maven release. So this project has no `pom.xml`. It builds the UI jar
itself: a jar is a zip file with `META-INF/MANIFEST.MF`, and this one holds no class.

### The UI jar and the nginx image

The UI jar `io.vanillabp.blueprint:cockpit-user-task-ui` holds the micro-frontends below
`META-INF/resources/loan-approval/`. A Java application with the jar on its class path serves
them at `/loan-approval/`, so the manifest is at `/loan-approval/bc-manifest.json`. The version is
the one in `package.json`. To publish the jar, upload it and its pom into a Maven repository with
HTTP `PUT`, or with `mvn deploy:deploy-file` where Maven is at hand:

```bash
mvn deploy:deploy-file -Dfile=dist/cockpit-user-task-ui-1.0.0-SNAPSHOT.jar \
  -DpomFile=dist/cockpit-user-task-ui-1.0.0-SNAPSHOT.pom \
  -DrepositoryId=github -Durl=https://maven.pkg.github.com/<owner>/<repository>
```

The nginx image serves the same files at the same paths, for an application which forwards to an
nginx instead of serving the files itself. `bc-manifest.json` is sent with
`Cache-Control: no-cache`. Every other file has the hash of its content in its name, so the image
lets browsers keep it for good.

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
