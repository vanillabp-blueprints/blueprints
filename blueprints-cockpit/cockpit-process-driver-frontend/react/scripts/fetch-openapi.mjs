// Copies the two OpenAPI files of the backend to build/openapi/<name>.yaml, where
// `npm run generate:client` reads them. It also empties src/generated, so no class of an older API
// stays behind there.
//
//   loan-approval    the API of the use case, which the forms call
//   process-driver   the API of the process driver, which only the dev shell calls
//
// The backend owns both APIs, and this project only reads their OpenAPI files. The files are the
// contract between the two. Each file comes from the first of these places which has it:
//
//   1. OPENAPI_FILE_<NAME>         the path of an OpenAPI file, for example a draft of a new API,
//                                  as OPENAPI_FILE_LOAN_APPROVAL or OPENAPI_FILE_PROCESS_DRIVER
//   2. config.openapi              the file in the git submodule `backend`: the backend
//                                  repository at a pinned commit, see .gitmodules
//   3. config.openapiInMonorepo    the same file in the monorepo `blueprints`. There the backend
//                                  is a directory of its own, and there is no submodule.
//                                  Delete this entry in a project of your own
//
// The paths in package.json are relative to this project.

import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const projectDir = resolve(import.meta.dirname, '..');
const { config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const targetDir = join(projectDir, 'build', 'openapi');
const generatedDir = join(projectDir, 'src', 'generated');

function findOpenApi(name) {
  const variable = `OPENAPI_FILE_${name.toUpperCase().replaceAll('-', '_')}`;
  if (process.env[variable]) return resolve(process.env[variable]);
  const found = [config.openapi?.[name], config.openapiInMonorepo?.[name]]
    .filter(Boolean)
    .map((path) => join(projectDir, path))
    .find((file) => existsSync(file));
  if (found) return found;
  throw new Error([
    `found no OpenAPI file at ${config.openapi?.[name]}.`,
    'The submodule `backend` is probably empty. Fill it with',
    '  git submodule update --init',
    'or clone with `git clone --recurse-submodules`. To build against another file, set',
    `  ${variable}=<path of an OpenAPI file>`,
  ].join('\n'));
}

try {
  mkdirSync(targetDir, { recursive: true });
  for (const name of Object.keys(config.openapi)) {
    const file = findOpenApi(name);
    copyFileSync(file, join(targetDir, `${name}.yaml`));
    console.log(`fetch-openapi: ${relative(projectDir, file)}`);
  }
  rmSync(generatedDir, { recursive: true, force: true });
} catch (error) {
  console.error(`fetch-openapi: ${error.message}`);
  process.exit(1);
}
