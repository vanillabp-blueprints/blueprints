![Header](./readme/vanillabp-headline.png)

# cockpit-user-task-frontend-angular

The forms of two user tasks for a VanillaBP Business Cockpit, written in Angular. The cockpit
loads them at runtime from the workflow module `loan-approval`. This project builds them with
npm and nothing else, and it delivers them in two ways: as a UI jar for a Java application, and
as an nginx image.

It is one of three parts of the blueprint `cockpit-user-task`:

|                 Part                 |                                   What it holds                                   |
|--------------------------------------|-----------------------------------------------------------------------------------|
| `cockpit-user-task-backend`          | the workflow module and its API as an OpenAPI file, built with Maven              |
| `cockpit-user-task-frontend-angular` | this project: the micro-frontends, the UI jar and the nginx image, built with npm |
| `cockpit-user-task-application`      | takes the workflow module and the UI jar and makes a runnable application of them |

There are two more frontends with the same content, one in React and one in Vue. All three
build a UI jar with the same Maven coordinates and use the same test ids. The application does
not know which one it gets.

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
- browser tests which load the build the way a cockpit does, without a cockpit,
- Angular inside web components: one Angular application for all of them, without zone.js.

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
A cockpit generated with the skill `business-cockpit-creator` brings its own types library
with the same minimum. It may offer more, for example more areas or more entries. If you start
from this blueprint, replace the kit with the types library of your cockpit later. The code
stays the same where it uses the minimum.

## Running it

You need Node.js 24, git and Docker. Docker builds the nginx image.

Clone the repository with its submodule, which brings the OpenAPI file of the backend:

```bash
git clone --recurse-submodules https://github.com/vanillabp-blueprints/cockpit-user-task-frontend-angular.git
```

If you cloned without it, `git submodule update --init` fetches the submodule later.

### The module kit

`@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host` come from npmjs.com like every other
package. While the kit is a prerelease, `package.json` pins both versions exactly. To take a newer
snapshot, raise the pinned version and run `npm install`.

### Build and test

```bash
npm ci
npx playwright install chromium-headless-shell   # once, for the browser tests
npm run build
npm test
```

|             Script              |                                                                 What it does                                                                  |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                                                                     |
| `npm run fetch:openapi`         | copies the OpenAPI file of the backend into `build/openapi`                                                                                   |
| `npm run generate:client`       | generates the API client from it with `@hey-api/openapi-ts`                                                                                   |
| `npm run write:bc-build`        | writes the module id and the id of this build into `src/bc-build.js`, see [The names of the web components](#the-names-of-the-web-components) |
| `npm run build:micro-frontends` | `ng build`, the Angular CLI: checks types and templates, builds the entries into `dist/micro-frontends`, then writes `bc-manifest.json`       |
| `npm run check:entries`         | runs `bc-check-entries` of the kit: no bare import, no framework in a data entry, every file there                                            |
| `npm run build:jar`             | builds the UI jar and its pom into `dist`                                                                                                     |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run                                                     |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                                                                          |
| `npm run start`                 | the dev shell. Not there yet, see below                                                                                                       |

### The OpenAPI file of the backend

The forms call the API of the workflow module. The backend describes that API in an OpenAPI
file. This project generates its client from that file, so a changed API breaks the build
here and not the form in the browser. The file is the contract between the two projects, and it
is the only thing this project takes from the backend.

The file comes in through a git submodule. `backend/` is the backend repository at a fixed
commit, and `.gitmodules` says which repository that is. The file lies in the workflow module of
the backend, and `config.openapi` in `package.json` names it:

```text
backend/loan-approval/src/main/resources/loan-approval/openapi/loan-approval.yaml
```

Why a submodule and not a released jar? The frontend builds without the backend being built or
released, and without Java. It needs no Maven repository and no login to one. The commit of the
submodule says exactly which state of the API the frontend was built against, and a pull request
which moves it shows the change of the API in its diff.

`scripts/fetch-openapi.mjs` copies the file to `build/openapi/loan-approval.yaml`. It also checks
that the file names a server, because the generator makes the first one, `/wm/loan-approval`, the
base URL of the client. To build against another file, for example a draft of a new API, set
`OPENAPI_FILE` to its path.

To take a newer API, move the submodule to the newest commit of the backend and commit that:

```bash
git submodule update --remote backend
git add backend
git commit -m "build: take the newest API of the backend"
```

Renovate can do the same with its manager `git-submodules`. It is off by default, so switch it on
in `renovate.json`:

```json
{
  "git-submodules": { "enabled": true }
}
```

Then Renovate opens a pull request whenever the branch `main` of the backend moves on.

In the monorepo [`blueprints`](https://github.com/vanillabp-blueprints/blueprints), where this
project is developed, the backend is a directory next to this one, and there is no submodule.
There the script reads the file named in `config.openapiInMonorepo`. The job which copies this
project into its own repository adds the submodule. In your own project, delete
`config.openapiInMonorepo`.

### The generated client

`npm run generate:client` runs [`@hey-api/openapi-ts`](https://heyapi.dev/openapi-ts/get-started).
It names things the way the Java code of the backend is named. The class name comes from the tag
of the OpenAPI file: the tag `user-tasks` becomes the class `UserTasksApi`. Each operation becomes
a method, and each schema a type of the same name.

`openapi-ts.config.mjs` sets the options of the generator:

- one class per tag, named like the Java client,
- a method takes one object with all its parameters, and the body comes under the name of its
  schema,
- a method returns the data of the answer, and it throws when the answer is not a success.

`src/shared/api.ts` creates the one instance which the forms use. It is named after its role,
the API of the workflow module:

```ts
export const loanApproval = new UserTasksApi();

const form = await loanApproval.getAssessRiskForm({ loanRequestId, taskId });
await loanApproval.completeAssessRisk({ loanRequestId, taskId, assessRiskInput: { riskAssessment } });
```

The client uses the `fetch` of the browser. The generator copies the code for it into the
generated files, so the forms need no library for it at runtime. When an answer is not a success,
the generated client throws only its body, and the OpenAPI file gives errors no body. So
`api.ts` adds an error interceptor, which throws a `ResponseError` instead. Its `response` has the
status, and that is how a form tells the answers apart. 404 means that the task is not open any
more. 409 means that somebody else took the task over in the cockpit, so the form says so and
completes nothing. Every other answer is a failure the user may try again. A network error brings
no answer, and the call throws what `fetch` threw.

The generator is a Node program and an exact dev dependency in `package.json`. It has no install
scripts, and it downloads nothing when it runs. So the build needs Node and nothing else: no Java,
which many build agents for Node do not have, and no access to Maven Central. The version is
pinned exactly, because the 0.x line of the generator breaks things in minor versions. Check a
new version by hand before you take it.

The build writes the generated code to `src/generated/loan-approval` every time, and git ignores
it. The backend does the same with its Java code, which it generates below `target/`. So the
OpenAPI file is the only source of the client, and nobody edits generated code by hand.

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

The dev shell comes from a cockpit generated with the skill `business-cockpit-creator`.
Such a cockpit publishes it as the package `@acme/bc-dev-shell-angular`, with the scope of the
organisation instead of `@acme`. None of them publishes it yet. Until one does, `npm run start`
only says so, and the browser tests are the way to see the forms.

## How it works

|                  File                  |                                                     Role                                                     |
|----------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                 | the names shared with the backend and the cockpit: module id, process id, task definitions, entries          |
| `src/data/user-task-form.ts`           | data entry `UserTaskForm`: picks the form by `bpmnProcessId` and `taskDefinition`                            |
| `src/data/user-task-list.ts`           | data entry `UserTaskList`: the column `details.riskAssessment` of the task list                              |
| `src/forms/assess-risk-form.ts`        | visual entry `AssessRiskForm`: the form of "Assess risk"                                                     |
| `src/forms/check-documents-form.ts`    | visual entry `CheckDocumentsForm`: the form of "Check documents"                                             |
| `src/shared/input-form.ts`             | the four components of a form with one input field, and the messages between its areas                       |
| `src/shared/angular-element.ts`        | makes an Angular component a web component with `defineBcElement` of the kit                                 |
| `src/shared/api.ts`                    | `loanApproval`, the one instance of the generated `UserTasksApi` and `ResponseError`, which keeps the status |
| `src/entries.ts`                       | the entry point of `ng build`: one `import()` per entry, so each entry becomes an ES module of its own       |
| `scripts/write-manifest.mjs`           | writes `bc-manifest.json` with `createManifest` from the list of outputs of the Angular CLI                  |
| `scripts/build-ui-jar.mjs`             | builds the UI jar with a zip library                                                                         |
| `docker/Dockerfile`                    | the nginx image                                                                                              |
| `test/browser/user-task-forms.test.js` | the browser tests in the host of the kit                                                                     |

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
   form, so this step loads no Angular.
4. The cockpit loads the visual entry `AssessRiskForm`. Only now does the browser load Angular, in a
   chunk the two forms share. The other form is never loaded.
5. A cockpit with the areas `body`, `information` and `actions` draws those three web
   components. A cockpit without areas draws the one of `all`. Each web component draws into its
   own shadow DOM.
6. The form loads its data from
   `/wm/loan-approval/api/loan-requests/{loanRequestId}/assess-risk/{taskId}`. The loan request
   is the business id of the user task, and the module checks that the task belongs to it. The
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

### The names of the web components

All modules of a cockpit define their web components in the one registry of the page. A name
can be defined only once there, and the cockpit refuses a name which does not start with the
prefix of its module. So every name has three parts, built by `createElementNames` of the kit:

1. the prefix of the workflow module id, `loan-approval`,
2. the id of the build, the time of the build in milliseconds in base 36,
3. the BPMN process id, the task definition and the part, `form` for the area `all` and the name of the area for the others.

`elementName.userTask(BPMN_PROCESS_ID, ASSESS_RISK, 'form')` in `src/loan-approval.ts` gives `loan-approval-<build id>-loan-approval--assess-risk--form`. A new build gets new names, so the
cockpit loads it while somebody has the cockpit open, without a reload of the page.

`npm run write:bc-build` (`scripts/write-bc-build.mjs`) writes `src/bc-build.js` before the type
check: `WORKFLOW_MODULE_ID` from `config.workflowModuleId` in `package.json`, and `BUILD_ID`.
`scripts/write-manifest.mjs` passes the same time to `createManifest`, so the manifest and
the names speak of one build. The file is written for every build and not checked in, its types
are in `src/bc-build.d.ts`. The browser tests read the build id from it, and
`test/build-id.test.js` proves that two builds give different names.

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

The Angular CLI reads the same `paths`, so the build needs nothing more for the aliases. The
browser tests import no source file, they load the build. So they need no aliases.

`npm run build` starts with `npm run check:imports`. `scripts/check-imports.mjs` fails the build
if a file in `src` imports with `../`, so the rule stays. The generated client is left out. The
project has no ESLint, which is why it is a script and not a lint rule.

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
neither Angular nor a form, that opening one form does not load the other, and that the typed text
stays when the cockpit sets a property such as the language, and that a 404 and a 409 each show
their own message.

### Angular inside web components

An Angular application normally owns the whole page. Here the page belongs to the cockpit, and
Angular only draws inside the web components of this module. That changes how the project is
built and how the components are written.

The project builds with the Angular CLI and its `application` builder from `@angular/build`, the
way every Angular project builds. So the usual Angular documentation and the usual answers on the
web apply. The CLI compiles ahead of time, checks the templates and bundles with esbuild inside.

The cockpit needs one ES module per entry and a manifest, not one application. `angular.json`
asks for no `index.html` and no polyfills, and its entry point `src/entries.ts` imports each entry
with `import()`. The CLI writes each of these as a file of its own with the hash of its content
in the name, the same way it writes a lazy loaded route, and puts Angular into a chunk the two
forms share. The cockpit never loads `src/entries.ts` itself, it loads the entries, so
`scripts/write-manifest.mjs` deletes the file the CLI writes for it, `main-<hash>.js`. With
`statsJson` the CLI also writes its list of outputs. `scripts/write-manifest.mjs` takes the file of
each entry from that list and writes `bc-manifest.json`. That script is the only part of the build
which is not Angular's own.

All web components of this module share one Angular application. `createApplication` creates it
when the first web component is drawn, and `createComponent` draws each component into the shadow
root of its web component. The properties of the cockpit come in as the signal input `props`.
`src/shared/angular-element.ts` holds this code.

There is no zone.js. It would patch timers, promises and events of the whole page, so it would
change the cockpit too. The application runs with `provideZonelessChangeDetection()`, and the
components keep their state in signals, which tell Angular when to draw again.

The components have no styles of their own, and `angular-element.ts` puts one `<style>` element
into each shadow root. Angular would write the styles of a component into the shadow root as
well, because `angular-element.ts` puts the host element there before it creates the component.
But the data to read are drawn with `NgComponentOutlet`, and Angular creates the host element of
such a component outside the page first. From then on it writes every style of the module into
the `<head>` of the page too, where it reaches the cockpit. The browser tests check that the page
keeps its own style only.

The host element of an Angular component gets the name of its selector. If that name is also the
name of a web component, the browser turns the host element into that web component, and the
form disappears inside it. So no selector here is the name of a web component of this module.

The four components in `src/shared/input-form.ts` serve both forms. What differs, for example the
label or the API call, comes in through the injection token `INPUT_FORM`. The data to read come
from a small component in each form file, drawn with `NgComponentOutlet`.

The Angular compiler accepts one minor line of TypeScript only, so `package.json` pins TypeScript
to it. `openapi-typescript` asks for TypeScript 5. An `overrides` entry in `package.json` lets it
use the TypeScript of the project. It only generates types, and the newer TypeScript does that
just as well.

### Three repositories, and the order of their releases

Many companies give their build agents one Docker image with Java and Maven and another one with
Node. A repository where Maven and npm build together is hard to have there. So a workflow
module with a user interface has three parts, each built with one tool: the backend with Maven,
this frontend with npm, and the application with Maven again.

The frontend needs only the API, and it takes the OpenAPI file from the backend repository
through its submodule. Only the application needs both. So there is no cycle, and a release goes
in this order:

1. the backend, which releases the workflow module,
2. the frontend, which releases the UI jar, built against the API at the commit of its submodule,
3. the application, which takes the released workflow module and the released UI jar.

The frontend does not wait for a release of the backend. It can build against a commit of the
backend which is not released yet. Before a release, check that the submodule points at the
commit of the backend release, or at one with the same API. Renovate proposes new versions to
the application as pull requests. More repositories to release is the price for build agents
with one tool each.

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
lets browsers keep it for good. The JavaScript files and the manifest go out compressed with gzip
when the browser asks for it, which is about a third of the bytes.

## Documentation

- [Loading the parts of a workflow module](https://github.com/vanillabp/business-cockpit/wiki/Loading-the-parts-of-a-workflow-module): entries, the manifest, areas and the event protocol
- [The module kit](https://github.com/vanillabp/business-cockpit-module-kit): `@vanillabp/bc-module-kit` and `@vanillabp/bc-module-host`
- [`@hey-api/openapi-ts`](https://heyapi.dev/openapi-ts/get-started): the generator of the API client
- [Angular without ZoneJS](https://angular.dev/guide/zoneless): change detection driven by signals, as the components here use it
- [Programmatically rendering components](https://angular.dev/guide/components/programmatic-rendering): `createComponent`, which draws a component into a shadow root

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
