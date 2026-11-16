// Copies the OpenAPI file of the workflow module to build/openapi/loan-approval.yaml, where
// `npm run generate:client` reads it. It also empties src/generated, so no class of an older API
// stays behind there. The generator makes the first server URL of the file the base URL of the
// client, so this script makes sure the file names one.
//
// The backend owns the API, and this project only reads its OpenAPI file. The file is the
// contract between the two. It comes from the first of these places which has it:
//
//   1. OPENAPI_FILE                the path of an OpenAPI file, for example a draft of a new API
//   2. config.openapi              the file in the git submodule `backend`: the backend
//                                  repository at a pinned commit, see .gitmodules
//   3. config.openapiInMonorepo    the same file in the monorepo `blueprints`. There the backend
//                                  is a directory next to this one, and there is no submodule.
//                                  Delete this entry in a project of your own
//
// Both paths in package.json are relative to this project.

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

const projectDir = resolve(import.meta.dirname, '..');
const { config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const target = join(projectDir, 'build', 'openapi', `${config.workflowModuleId}.yaml`);
const generatedDir = join(projectDir, 'src', 'generated');

function findOpenApi() {
  if (process.env.OPENAPI_FILE) return resolve(process.env.OPENAPI_FILE);
  const found = [config.openapi, config.openapiInMonorepo]
    .filter(Boolean)
    .map((path) => join(projectDir, path))
    .find((file) => existsSync(file));
  if (found) return found;
  throw new Error([
    `found no OpenAPI file at ${config.openapi}.`,
    'The submodule `backend` is probably empty. Fill it with',
    '  git submodule update --init',
    'or clone with `git clone --recurse-submodules`. To build against another file, set',
    '  OPENAPI_FILE=<path of an OpenAPI file>',
  ].join('\n'));
}

/** The first server URL of the OpenAPI file, read without a YAML library. */
function serverUrl(yaml, origin) {
  const url = yaml.match(/^servers:[ \t]*\r?\n[ \t]+- url:[ \t]*['"]?([^'"\s#]+)/m)?.[1];
  if (!url) throw new Error(`${origin} names no server URL, and the API client needs it as its base URL`);
  return url;
}

try {
  const file = findOpenApi();
  const yaml = readFileSync(file, 'utf8');
  const url = serverUrl(yaml, file);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, yaml);
  rmSync(generatedDir, { recursive: true, force: true });
  console.log(`fetch-openapi: ${relative(projectDir, file)}, server ${url}`);
} catch (error) {
  console.error(`fetch-openapi: ${error.message}`);
  process.exit(1);
}
