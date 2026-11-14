![Header](./readme/vanillabp-headline.png)

# cockpit-header-extension-frontend-react

The button "Request a loan" for the header of a VanillaBP Business Cockpit, and the start form
it opens, written in React. The cockpit loads both at runtime from the workflow module
`loan-approval`. This project builds them with npm and nothing else, and it delivers them in two
ways: as a UI jar for a Java application, and as an nginx image.

It is one of three parts of the blueprint `cockpit-header-extension`:

|                   Part                    |                                   What it holds                                   |
|-------------------------------------------|-----------------------------------------------------------------------------------|
| `cockpit-header-extension-backend`        | the workflow module and its API as an OpenAPI file, built with Maven              |
| `cockpit-header-extension-frontend-react` | this project: the micro-frontends, the UI jar and the nginx image, built with npm |
| `cockpit-header-extension-application`    | takes the workflow module and the UI jar and makes a runnable application of them |

The project is built like
[`cockpit-workflow-details-frontend-react`](https://github.com/vanillabp-blueprints/cockpit-workflow-details-frontend-react),
which shows the page of a case, and like
[`cockpit-user-task-frontend-react`](https://github.com/vanillabp-blueprints/cockpit-user-task-frontend-react),
which shows the forms of user tasks. The README of the second one explains what this one only
links: micro-frontends and the two kinds of entries, why the parts come at runtime, the module kit
as the minimum, the OpenAPI file through a git submodule, why npm builds apart from Maven, and the
UI jar and the nginx image.

## What this blueprint shows

A workflow module can fill parts of the cockpit itself, not only the forms of its user tasks and
the pages of its cases. The most important case is starting a process: a loan request which does
not exist yet has no task and no page where a button could be. So this module puts a button into
the header of the cockpit. This project shows:

- one data entry `Header` of the module's own, which names the button. `bc-check-entries` checks
  it with `--data-entry Header`, because it is not one of the four entries the kit knows,
- the button and the start form as a visual entry of their own, which a cockpit only loads when it
  draws its header,
- the start form in a modal dialog of the cockpit, opened with `openDialog` and closed with
  `closeDialog`,
- the OK of the form, which posts to the module API. The module starts the process, and the
  cockpit learns about the new case from the module,
- that the cockpit decides who sees the button, by the list of modules the user may see,
- browser tests which load the build the way a cockpit does, without a cockpit: with a header and
  without, and next to a second module which has no header part.

### A cockpit decides whether it has a header

The module kit is the minimum every cockpit keeps: four data entries, the area `all`, the
manifest and the event protocol. A header with buttons of the modules is not part of it. Each
organisation generates its own cockpit with the skill `business-cockpit-creator`, and the skill
asks whether the cockpit gets a header with room for modules. It proposes yes.

A cockpit with a header asks the entry `Header` of every module the user may see. A cockpit
without a header does not know the entry. It never asks it and never loads it, and it shows no
error. So this module runs in both. In the second one only the button is missing.

### Who sees the button

The data entry checks no rights. A cockpit user interface reads the list of modules with
`GET /gui/api/v1/workflow-module`, and the cockpit leaves out every module whose groups the user
is not in. The backend names the group `LOAN_OFFICERS`. A cockpit asks the entry `Header` only of
the modules in the list. So a user outside the group never sees the button, and the browser does
not even load the file of the entry. The README of the backend says more.

### Other parts of the cockpit a module can fill

The skill knows more extension points than the header. Two are common: an entry in the menu, and
a page of the module which belongs to no case, for example a page to keep the master data a
process needs. The skill calls the second `Pages` and does not propose it by default. This
blueprint builds neither. A module which offers pages adds a data entry `Pages` of its own and
names it for `bc-check-entries` with `--data-entry Pages`, as this one does with `Header`.

## Running it

You need Node.js 24, git and Docker. Docker builds the nginx image.

Clone the repository with its submodule, which brings the OpenAPI file of the backend:

```bash
git clone --recurse-submodules https://github.com/vanillabp-blueprints/cockpit-header-extension-frontend-react.git
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

|             Script              |                                                         What it does                                                          |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `npm run build`                 | all of the following, one after the other                                                                                     |
| `npm run fetch:openapi`         | copies the OpenAPI file of the backend into `build/openapi`                                                                   |
| `npm run generate:client`       | generates the types of the API client from it                                                                                 |
| `npm run typecheck`             | checks the types of the whole project                                                                                         |
| `npm run build:micro-frontends` | builds the entries and `bc-manifest.json` into `dist/micro-frontends`                                                         |
| `npm run check:entries`         | runs `bc-check-entries` of the kit with `--data-entry Header`: no bare import, no framework in a data entry, every file there |
| `npm run build:jar`             | builds the UI jar `io.vanillabp.blueprint:cockpit-header-extension-ui` and its pom into `dist`                                |
| `npm run build:image`           | builds the nginx image. `npm run build` skips it with a warning where Docker does not run                                     |
| `npm test`                      | the browser tests, see [How it works](#how-it-works)                                                                          |
| `npm run start`                 | the dev shell. Not there yet                                                                                                  |

### With the backend during development

The start form posts to `/wm/loan-approval/api/loan-requests`. During development the dev shell
forwards `/wm/loan-approval/` to the workflow module on your machine. The README of the backend
says how to start it, in "Running the backend for the development of the user interface". The
dev shell comes from a cockpit generated with the skill `business-cockpit-creator`, as the package
`@acme/bc-dev-shell-react`. The skill says that its dev shell shows the buttons of the header like
the cockpit does. None publishes the package yet, so `npm run start` only says so, and the browser
tests are the way to see the button and the form.

## How it works

|                 File                  |                                                 Role                                                  |
|---------------------------------------|-------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`                | the names shared with the backend and the cockpit: module id, entry, web components                   |
| `src/data/header.ts`                  | data entry `Header`: `header(currentUser)` names the visual entry and the web component of the button |
| `src/start-form/start-form.tsx`       | visual entry `StartForm`: the button, which opens the dialog, and the start form, which closes it     |
| `src/shared/api.ts`                   | the client of the module API, typed by the generated `src/generated/loan-approval.ts`                 |
| `src/shared/react-element.tsx`        | makes a React component a web component with `defineBcElement` of the kit                             |
| `src/shared/style.ts`                 | the styles in each shadow root: a button one line high, a form which wraps long words                 |
| `scripts/build-micro-frontends.mjs`   | builds the entries with esbuild and writes `bc-manifest.json` with `createManifest` of the kit        |
| `test/browser/header.test.js`         | the browser tests in the host of the module kit                                                       |
| `test/browser/module-without-header/` | a second module for the tests, with a form of a user task and no entry `Header`                       |

### From the header to a new case

1. The cockpit reads the list of modules the user may see. `loan-approval` is in it for a loan
   officer.
2. A cockpit with a header loads the data entry `Header` of every module in the list and calls
   `header(currentUser)`. This one returns
   `[{ entry: 'StartForm', element: 'loan-approval-request-loan-button' }]`. It imports no
   React, so this step loads no React. A module without the entry is skipped.
3. The cockpit loads the visual entry `StartForm`. Only now does the browser load React. It draws
   the web component of the button into its header, in a shadow DOM of its own.
4. The user clicks "Request a loan". The button calls
   `openDialog({ entry: 'StartForm', element: 'loan-approval-request-loan-form' })`. The kit turns
   the call into a `bc-request` event, and the cockpit draws the form in a modal dialog. The entry
   is loaded already.
5. The user types the applicant and the amount and clicks OK. The form checks both and posts them
   to `/wm/loan-approval/api/loan-requests`. The module starts the process and answers with the id
   of the new loan request.
6. The form calls `closeDialog(answer)`. The cockpit closes the dialog, and the promise of
   `openDialog` resolves with the answer. The button calls `showNotification` with the id.
7. The cockpit adapter of the module reports the new case, and the case list shows it.

"Cancel" closes the dialog with no answer, and nothing is sent. A refused or failed request keeps
the dialog open with a message, so the user can correct the values or try again.

### Test ids

|        Test id         |                             Element                             |
|------------------------|-----------------------------------------------------------------|
| `request-loan`         | the button "Request a loan" in the header                       |
| `start-form`           | the start form in the dialog                                    |
| `start-form-applicant` | the field of the applicant                                      |
| `start-form-amount`    | the field of the amount                                         |
| `start-form-ok`        | the button OK                                                   |
| `start-form-cancel`    | the button "Cancel"                                             |
| `start-form-error`     | the message shown when a value is missing or the module refuses |

### The browser tests

`npm test` starts a small web server and Chromium. The test page loads the build through
`createModuleHost` of `@vanillabp/bc-module-host`, as a cockpit would. There is no cockpit and no
backend. The tests answer the module API inside the browser, at the network level. The test page
draws the header the way a cockpit with a header does: it asks the entry `Header` of every module
in the list of modules, loads what the answers name and draws it.

The tests check the data entry and that it loads no React, the button, the dialog with OK and with
"Cancel", the values the form refuses, the answers 400 and 503 of the module, the texts in German,
a user for whom the cockpit does not list the module, a second module without the entry `Header`,
and a cockpit without a header, whose host does not know the entry.

## Documentation

- [Loading the parts of a workflow module](https://github.com/vanillabp/business-cockpit/blob/main/skills/business-cockpit-creator/references/module-loading.md#the-other-extension-points): the extension point `Header`, dialogs and the event protocol
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
