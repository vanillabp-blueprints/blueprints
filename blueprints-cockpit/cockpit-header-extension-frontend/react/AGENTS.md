# cockpit-header-extension-frontend-react

The button "Request a loan" for the header of a VanillaBP Business Cockpit, and the start form it
opens in a dialog of the cockpit, for the workflow module `loan-approval`, in React: a data entry
`Header` of the module's own and a visual entry with the button and the form. An npm project
without Java. It builds the micro-frontends, the UI jar and an nginx image. The backend with the
API and the application are separate projects. The OpenAPI file of the backend comes in through
the git submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This project is built like `cockpit-workflow-details-frontend-react` and
`cockpit-user-task-frontend-react`. The scripts, the UI jar and the image are the same.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                      Meaning                                                       |
|----------------------------|--------------------------------------------------------------------------------------------------------------------|
| `loan-approval`            | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name |
| `cockpit-header-extension` | blueprint id: the names of the backend repository, the UI jar and the image                                        |

`loan-approval` is in `src/loan-approval.ts` and in `package.json` (`config`).

The web component names start with the workflow module id. Web components share one registry
per page, and the header of a cockpit holds the buttons of several modules. Two modules which
define the same name break each other.

Blueprint-specific names:

|    Name     |                                                 Where it occurs                                                  |
|-------------|------------------------------------------------------------------------------------------------------------------|
| `Header`    | the entry in `scripts/build-micro-frontends.mjs`, `--data-entry Header` in `package.json`, the host of the tests |
| `header`    | the function the data entry exports. A cockpit with a header calls it by this name                               |
| `StartForm` | the visual entry: `src/loan-approval.ts` and `scripts/build-micro-frontends.mjs`                                 |

`Header` and `header` are the names the skill proposes. A generated cockpit may name its
extension point differently. Then its types library says so, and these two names follow it.

## Core files

|                File                 |                                                                Why it matters                                                                 |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`              | module id, entry name and web component names, in one place                                                                                   |
| `src/data/header.ts`                | data entry `Header`: returns `[{ entry, element }]` of the button. Plain TypeScript, it must never import React or the form; checks no rights |
| `src/start-form/start-form.tsx`     | visual entry `StartForm`: the button with `openDialog`, the form with `closeDialog`, the call of the module API                               |
| `src/shared/api.ts`                 | the client of the module API, base path `/wm/loan-approval` from the server of the OpenAPI file                                               |
| `scripts/build-micro-frontends.mjs` | the entry list for esbuild, and `bc-manifest.json` from esbuild's outputs                                                                     |
| `package.json`                      | `check:entries` with `--data-entry Header`, without which `bc-check-entries` would not check the data entry                                   |
| `test/browser/header.test.js`       | the browser tests, with a header and without, and next to a module without the entry                                                          |

## Boilerplate files

|                 File                  |                                      Purpose                                      |
|---------------------------------------|-----------------------------------------------------------------------------------|
| `tsconfig.json`                       | type checking only; esbuild builds                                                |
| `scripts/fetch-openapi.mjs`           | copies the OpenAPI file from the submodule, and its server as the client base URL |
| `.gitmodules`                         | the submodule `backend`, the backend repository which owns the OpenAPI file       |
| `scripts/build-ui-jar.mjs`            | the UI jar and its pom                                                            |
| `scripts/build-image.mjs`             | the nginx image                                                                   |
| `scripts/start.mjs`                   | placeholder for the dev shell                                                     |
| `src/shared/react-element.tsx`        | a React component as a web component, with `defineBcElement` of the kit           |
| `src/shared/style.ts`                 | the styles, put into each shadow root                                             |
| `src/shared/texts.ts`                 | the texts in English and German                                                   |
| `docker/Dockerfile`                   | the nginx image                                                                   |
| `docker/nginx.conf`                   | serves `/loan-approval/`, the manifest without caching                            |
| `test/browser/page.js`                | the test page with `createModuleHost`, which draws the header like a cockpit      |
| `test/browser/server.js`              | serves the test page, `dist/micro-frontends` and the second module                |
| `test/browser/module-without-header/` | the second module of the tests: a manifest without `Header`                       |

## Adding this blueprint to an existing project

1. If the workflow module has no frontend project yet, copy this one and replace the
   placeholders. If it has one, take the files below `src` and the scripts it lacks.
2. Add `src/data/header.ts`. It returns the buttons as `{ entry, element }` and imports nothing
   but names. Do not check rights there. The cockpit asks it only of the modules the user may see.
3. Put the button and the form into one visual entry. The button opens the form with
   `openDialog({ entry, element })`, and the form closes itself with `closeDialog(result)`.
4. The form calls the module API, never the cockpit. The module starts the process.
5. Add both entries to the list in `scripts/build-micro-frontends.mjs`, and `--data-entry Header`
   to `check:entries` in `package.json`.
6. Point the submodule in `.gitmodules` at your backend repository
   (`git submodule add <url> backend`), set `config.openapi` in `package.json` to the OpenAPI file
   inside it, and delete `config.openapiInMonorepo`. If your cockpit has a types library of its
   own, replace `@vanillabp/bc-module-kit` with it, and take the names of its extension points.
7. Keep the data entry free of React. `npm run check:entries` fails otherwise.

## Verifying

```bash
npm ci
npx playwright install chromium-headless-shell
npm run build
npm test
```

`npm run build` needs the OpenAPI file: the submodule `backend/` filled
(`git submodule update --init`), the backend directory next to this one in the monorepo, or
`OPENAPI_FILE`. `bc-check-entries` has to pass with `--data-entry Header`. It runs inside
`npm run build` and says `Header: ... bytes`. Every browser test has to pass, the ones with a
header and the one without.

Do not report success without having run this.
