# cockpit-header-extension-frontend-vue

The button "Request a loan" for the header of a VanillaBP Business Cockpit, and the start form it
opens in a dialog of the cockpit, for the workflow module `loan-approval`, in Vue: a data entry
`Header` of the module's own and a visual entry with the button and the form. An npm project
without Java. It builds the micro-frontends, the UI jar and an nginx image. The backend with the
API and the application are separate projects. The OpenAPI file of the backend comes in through
the git submodule `backend/`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This project is built like `cockpit-workflow-details-frontend-vue` and
`cockpit-user-task-frontend-vue`. The scripts, the UI jar and the image are the same.
`cockpit-header-extension-frontend-react` is the same button and form in React, with the same
tests.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                      Meaning                                                       |
|----------------------------|--------------------------------------------------------------------------------------------------------------------|
| `loan-approval`            | workflow module id: the path `/wm/loan-approval/`, the folder in the UI jar, the start of every web component name |
| `cockpit-header-extension` | blueprint id: the names of the backend repository, the UI jar and the image                                        |

`loan-approval` is in `src/loan-approval.ts` and in `package.json` (`config`).

The names of the web components come from `elementName` in `src/loan-approval.ts`, which is
`createElementNames` of the kit. A name is the prefix of the workflow module id, the id of the
build and a short name of its own, because the button and the start form show no workflow and no user task, for example `loan-approval-<build id>-request-loan-button`. Never write a name by hand. Web components
share one registry per page, so two modules which define the same name break each other, and
the browser defines a name only once, so a new build needs new names to load without a reload of
the page. `npm run build` writes the module id and the build id into `src/bc-build.js` first.

Blueprint-specific names:

|    Name     |                                                 Where it occurs                                                  |
|-------------|------------------------------------------------------------------------------------------------------------------|
| `Header`    | the entry in `scripts/build-micro-frontends.mjs`, `--data-entry Header` in `package.json`, the host of the tests |
| `header`    | the function the data entry exports. A cockpit with a header calls it by this name                               |
| `StartForm` | the visual entry: `src/loan-approval.ts` and `scripts/build-micro-frontends.mjs`                                 |

`Header` and `header` are the names the skill proposes. A generated cockpit may name its
extension point differently. Then its types library says so, and these two names follow it.

## Core files

|                File                 |                                                                                    Why it matters                                                                                     |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/loan-approval.ts`              | module id, entry name, web component names and the feature role `LOAN_OFFICERS`, in one place                                                                                         |
| `src/data/header.ts`                | data entry `Header`: returns `[{ entry, element }]` of the button to a member of `LOAN_OFFICERS`, and nothing to anybody else. Plain TypeScript, it must never import Vue or the form |
| `src/start-form/start-form.ts`      | visual entry `StartForm`: defines the two web components from its own components and nothing else                                                                                     |
| `src/start-form/*.ce.vue`           | the button with `openDialog`, the form with `closeDialog` and the call of the module API                                                                                              |
| `src/shared/vue-element.ts`         | a Vue component as a web component: an application each, the properties through `provide`, the styles                                                                                 |
| `src/shared/api.ts`                 | `loanApproval`, the one instance of the generated `StartFormApi` and `ResponseError`; base URL `/wm/loan-approval` from the OpenAPI file                                              |
| `scripts/build-micro-frontends.mjs` | the entry list for esbuild, the plugin which compiles `.vue` files, and `bc-manifest.json` from esbuild's outputs; a new import from a Vue package goes into `frameworkImports`       |
| `package.json`                      | `check:entries` with `--data-entry Header`, without which `bc-check-entries` would not check the data entry                                                                           |
| `test/browser/header.test.js`       | the browser tests, with a header and without, next to a module without the entry, and for a user who sees the module but is no loan officer                                           |

## Boilerplate files

|                 File                  |                                                                         Purpose                                                                         |
|---------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tsconfig.json`                       | type checking with `vue-tsc`, templates included; esbuild builds; `paths` holds the aliases `@loan-approval`, `@shared`, `@gui-client` and `@generated` |
| `src/shared/index.ts`                 | what the other directories import as `@shared`                                                                                                          |
| `scripts/check-imports.mjs`           | fails `npm run build` on an import with `../`; import through an alias instead                                                                          |
| `scripts/fetch-openapi.mjs`           | copies the OpenAPI file from the submodule and checks that it names a server                                                                            |
| `scripts/write-bc-build.mjs`          | writes `src/bc-build.js`, the module id from `package.json` and the build id, before the type check                                                     |
| `src/bc-build.d.ts`                   | the types of `src/bc-build.js`, which is written for every build and not checked in                                                                     |
| `openapi-ts.config.mjs`               | the options of `@hey-api/openapi-ts`, the generator of the API client                                                                                   |
| `.gitmodules`                         | the submodule `backend`, the backend repository which owns the OpenAPI file                                                                             |
| `scripts/build-ui-jar.mjs`            | the UI jar and its pom                                                                                                                                  |
| `scripts/build-image.mjs`             | the nginx image                                                                                                                                         |
| `scripts/start.mjs`                   | placeholder for the dev shell                                                                                                                           |
| `src/shared/style.ts`                 | the style of `:host`, put into each shadow root before the styles of components                                                                         |
| `src/shared/texts.ts`                 | the texts in English and German                                                                                                                         |
| `docker/Dockerfile`                   | the nginx image                                                                                                                                         |
| `docker/nginx.conf`                   | serves `/loan-approval/`, the manifest without caching, JavaScript and JSON with gzip                                                                   |
| `test/browser/page.js`                | the test page with `createModuleHost`, which draws the header like a cockpit                                                                            |
| `test/browser/server.js`              | serves the test page, `dist/micro-frontends` and the second module                                                                                      |
| `test/browser/module-without-header/` | the second module of the tests: a manifest without `Header`                                                                                             |

## Adding this blueprint to an existing project

1. If the workflow module has no frontend project yet, copy this one and replace the placeholders.
   If it has one, take the files below `src`, the scripts it lacks and `openapi-ts.config.mjs`.
2. Add `src/data/header.ts`. It returns the buttons as `{ entry, element }` and imports nothing
   but names. The cockpit asks it only of the modules the user may see, so the access group of
   the module needs no check there. A button for a feature role checks that role in
   `currentUser.groups`, which the cockpit has already completed with the group hierarchies.
   Never make the backend return the feature role as the access group to hide a button.
3. Put the button and the form into one visual entry. The button opens the form with
   `openDialog({ entry, element })`, and the form closes itself with `closeDialog(result)`. The
   visual entry imports its own components and nothing else. It never imports a list of all
   components of the module, because then every entry would share one chunk with all of them.
   A dev shell which draws every part keeps such a list for itself.
4. The form calls the module API, never the cockpit. The module starts the process.
5. Add both entries to the list in `scripts/build-micro-frontends.mjs`, and `--data-entry Header`
   to `check:entries` in `package.json`.
6. Point the submodule in `.gitmodules` at your backend repository
   (`git submodule add <url> backend`), set `config.openapi` in `package.json` to the OpenAPI file
   inside it, and delete `config.openapiInMonorepo`. If your cockpit has a types library of its
   own, replace `@vanillabp/bc-module-kit` with it, and take the names of its extension points.
7. Keep the data entry free of Vue. `npm run check:entries` fails otherwise.
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
`OPENAPI_FILE`. `bc-check-entries` has to pass with `--data-entry Header`. It runs inside
`npm run build` and says `Header: ... bytes`. Every browser test has to pass, the ones with a
header, the one without, and the one for a user who sees the module but is no loan officer.

Do not report success without having run this.
