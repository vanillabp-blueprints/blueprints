// Copies the three OpenAPI files to build/openapi/<name>.yaml, where `npm run generate:client` reads
// them. It also empties src/generated, so no class of an older API stays behind there.
//
//   loan-approval      the API of the use case, which the forms call
//   process-driver     the API of the process driver, which only the dev shell calls
//   official-gui-api   the official API of the cockpit, which only the dev shell calls
//
// The backend owns the first two APIs, and this project only reads their OpenAPI files. The files
// are the contract between the two. Each of them comes from the first of these places which has
// it:
//
//   1. OPENAPI_FILE_<NAME>         the path of an OpenAPI file, for example a draft of a new API,
//                                  as OPENAPI_FILE_LOAN_APPROVAL or OPENAPI_FILE_PROCESS_DRIVER
//   2. config.openapi              the file in the git submodule `backend`: the backend
//                                  repository at a pinned commit, see .gitmodules
//   3. config.openapiInMonorepo    the same file in the monorepo `blueprints`. There the backend
//                                  is a directory of its own, and there is no submodule.
//                                  Delete this entry in a project of your own
//
// The cockpit owns the third API. Its OpenAPI file is not written by hand and not copied into
// this project. It comes out of the released jar of the Business Cockpit, see
// config.cockpitOpenapi: v1.yaml at the root of io.vanillabp.businesscockpit:official-gui-api-server.
// The jar has the version of the cockpit which the backend uses, the property
// businesscockpit.version in its pom.xml (config.cockpitVersion). So the client of the dev shell
// always fits the cockpit of the backend. That version of the cockpit must be deployed to a Maven
// repository before this project can build. The script downloads the jar with HTTP GET from the
// path Maven uses:
//
//   <repository>/<group as path>/<artifactId>/<version>/<artifactId>-<version>.jar
//
// By default the repository is Maven Central, which needs no login: releases come from
// repo1.maven.org, snapshots from the snapshot repository of Maven Central
// (config.cockpitRepository). The jar of a -SNAPSHOT version has a time stamp in its name, so the
// script reads <version>/maven-metadata.xml first and takes the newest jar it lists. These
// variables change what the script does for the cockpit's file:
//
//   OPENAPI_FILE_OFFICIAL_GUI_API  the path of an OpenAPI file. Then nothing is downloaded
//   BUSINESS_COCKPIT_VERSION       another version of the cockpit than the one of the backend
//   COCKPIT_REPOSITORY             a Maven repository of your own for releases and snapshots,
//                                  for example a mirror of Maven Central in Nexus or Artifactory
//   COCKPIT_REPOSITORY_TOKEN       the token of that repository, if it asks for a login. It is
//                                  sent as a bearer token
//
// The paths in package.json are relative to this project.

import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { unzipSync } from 'fflate';

const projectDir = resolve(import.meta.dirname, '..');
const { config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const targetDir = join(projectDir, 'build', 'openapi');
const generatedDir = join(projectDir, 'src', 'generated');

function variableOf(name) {
  return `OPENAPI_FILE_${name.toUpperCase().replaceAll('-', '_')}`;
}

function firstExisting(...paths) {
  return paths
    .filter(Boolean)
    .map((path) => join(projectDir, path))
    .find((file) => existsSync(file));
}

function emptySubmodule(path, variable) {
  return [
    `found nothing at ${path}.`,
    'The submodule `backend` is probably empty. Fill it with',
    '  git submodule update --init',
    'or clone with `git clone --recurse-submodules`. To build without it, set',
    `  ${variable}`,
  ].join('\n');
}

function findOpenApi(name) {
  const variable = variableOf(name);
  if (process.env[variable]) return resolve(process.env[variable]);
  const found = firstExisting(config.openapi?.[name], config.openapiInMonorepo?.[name]);
  if (found) return found;
  throw new Error(emptySubmodule(config.openapi?.[name], `${variable}=<path of an OpenAPI file>`));
}

/** The version of the cockpit which the backend uses, the property in its pom.xml. */
function cockpitVersion() {
  if (process.env.BUSINESS_COCKPIT_VERSION) return process.env.BUSINESS_COCKPIT_VERSION;
  const { pom, pomInMonorepo, property } = config.cockpitVersion;
  const file = firstExisting(pom, pomInMonorepo);
  if (!file) throw new Error(emptySubmodule(pom, 'BUSINESS_COCKPIT_VERSION=<version of the cockpit>'));
  const found = readFileSync(file, 'utf8').match(new RegExp(`<${property}>\\s*([^<\\s]+)\\s*</${property}>`));
  if (!found) throw new Error(`found no property <${property}> in ${relative(projectDir, file)}.`);
  return found[1];
}

async function download(url) {
  const token = process.env.COCKPIT_REPOSITORY_TOKEN;
  const response = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (response.ok) return response;
  throw new Error([
    `${url} answered ${response.status}.`,
    'Is this version of the Business Cockpit deployed to the repository? If your build reads',
    'from a repository of its own, set COCKPIT_REPOSITORY and, if it asks for a login,',
    'COCKPIT_REPOSITORY_TOKEN.',
  ].join('\n'));
}

/** The URL of the jar. The jar of a snapshot has a time stamp in its name, see maven-metadata.xml. */
async function jarUrl(artifact, version) {
  const [groupId, artifactId] = artifact.split(':');
  const snapshot = version.endsWith('-SNAPSHOT');
  const repository = process.env.COCKPIT_REPOSITORY
    ?? (snapshot ? config.cockpitRepository.snapshots : config.cockpitRepository.releases);
  const folder = `${repository.replace(/\/*$/, '/')}${groupId.replaceAll('.', '/')}/${artifactId}/${version}/`;
  if (!snapshot) return `${folder}${artifactId}-${version}.jar`;
  const metadata = await (await download(`${folder}maven-metadata.xml`)).text();
  // the jars without a classifier: <snapshotVersion><extension>jar</extension><value>...
  const newest = [...metadata.matchAll(/<snapshotVersion>([\s\S]*?)<\/snapshotVersion>/g)]
    .map(([, entry]) => entry)
    .filter((entry) => entry.includes('<extension>jar</extension>') && !entry.includes('<classifier>'))
    .map((entry) => entry.match(/<value>([^<]+)<\/value>/)?.[1])
    .filter(Boolean)
    .sort()
    .at(-1);
  if (!newest) throw new Error(`${folder}maven-metadata.xml lists no jar.`);
  return `${folder}${artifactId}-${newest}.jar`;
}

/** Writes the file `path` of the cockpit's jar `artifact` to build/openapi/<name>.yaml. */
async function fetchFromCockpitJar(name, { artifact, path }, version) {
  const url = await jarUrl(artifact, version);
  const jar = new Uint8Array(await (await download(url)).arrayBuffer());
  const file = unzipSync(jar, { filter: (entry) => entry.name === path })[path];
  if (!file) throw new Error(`${url} has no file ${path}.`);
  writeFileSync(join(targetDir, `${name}.yaml`), file);
  console.log(`fetch-openapi: ${path} of ${url}`);
}

try {
  mkdirSync(targetDir, { recursive: true });
  for (const name of Object.keys(config.openapi)) {
    const file = findOpenApi(name);
    copyFileSync(file, join(targetDir, `${name}.yaml`));
    console.log(`fetch-openapi: ${relative(projectDir, file)}`);
  }
  let version;
  for (const [name, source] of Object.entries(config.cockpitOpenapi)) {
    const file = process.env[variableOf(name)];
    if (file) {
      copyFileSync(resolve(file), join(targetDir, `${name}.yaml`));
      console.log(`fetch-openapi: ${file}`);
      continue;
    }
    version ??= cockpitVersion();
    await fetchFromCockpitJar(name, source, version);
  }
  rmSync(generatedDir, { recursive: true, force: true });
} catch (error) {
  console.error(`fetch-openapi: ${error.message}`);
  process.exit(1);
}
