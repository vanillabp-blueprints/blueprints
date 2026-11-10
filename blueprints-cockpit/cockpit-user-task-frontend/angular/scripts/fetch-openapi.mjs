// Takes the OpenAPI file of the module API out of the API jar and writes it to
// build/openapi/loan-approval.yaml, where `npm run generate:client` reads it.
//
// The backend owns the API. It builds the OpenAPI file into the jar
// io.vanillabp.blueprint:cockpit-user-task-api, and this project only reads it. The jar is looked
// for in this order, and the first one found wins:
//
//   1. OPENAPI_FILE           the path of an OpenAPI file, for example a draft of a new API
//   2. API_JAR                the path of the API jar, for example one a CI job built before
//   3. MAVEN_REPOSITORY_URL   a Maven repository, for example GitHub Packages. With
//                             MAVEN_USERNAME and MAVEN_PASSWORD, if it needs a login
//   4. the local Maven repository, ~/.m2/repository or MAVEN_LOCAL_REPOSITORY, which
//      `mvn install` of the backend fills
//   5. .openapi/loan-approval.yaml, a copy placed there by hand. Git ignores it
//
// The jar does not need Java to be read: it is a zip file.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { unzipSync } from 'fflate';

const projectDir = resolve(import.meta.dirname, '..');
const { config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const [groupId, artifactId, version] = config.apiJar.split(':');
const fileInJar = `openapi/${config.workflowModuleId}.yaml`;
const target = join(projectDir, 'build', 'openapi', `${config.workflowModuleId}.yaml`);
const handPlacedCopy = join(projectDir, '.openapi', `${config.workflowModuleId}.yaml`);
const jarName = `${artifactId}-${version}.jar`;
const artifactPath = `${groupId.replaceAll('.', '/')}/${artifactId}/${version}`;

function fromJar(jar, origin) {
  const entries = unzipSync(jar, { filter: (file) => file.name === fileInJar });
  if (!entries[fileInJar]) throw new Error(`${origin} has no file ${fileInJar}`);
  return { yaml: entries[fileInJar], origin };
}

async function download(path) {
  const base = process.env.MAVEN_REPOSITORY_URL.replace(/\/+$/, '');
  const headers = {};
  if (process.env.MAVEN_USERNAME) {
    const login = `${process.env.MAVEN_USERNAME}:${process.env.MAVEN_PASSWORD ?? ''}`;
    headers.authorization = `Basic ${Buffer.from(login).toString('base64')}`;
  }
  const response = await fetch(`${base}/${path}`, { headers });
  if (!response.ok) throw new Error(`${base}/${path} answered ${response.status}`);
  return { url: `${base}/${path}`, body: Buffer.from(await response.arrayBuffer()) };
}

/** A snapshot is stored with a time stamp in its name, which maven-metadata.xml tells. */
async function downloadJar() {
  let fileName = jarName;
  if (version.endsWith('-SNAPSHOT')) {
    const metadata = (await download(`${artifactPath}/maven-metadata.xml`)).body.toString('utf8');
    const jarVersion = [...metadata.matchAll(/<snapshotVersion>([\s\S]*?)<\/snapshotVersion>/g)]
      .map(([, block]) => block)
      .find((block) => /<extension>jar<\/extension>/.test(block) && !/<classifier>/.test(block))
      ?.match(/<value>([^<]+)<\/value>/)?.[1];
    if (jarVersion) fileName = `${artifactId}-${jarVersion}.jar`;
  }
  const { url, body } = await download(`${artifactPath}/${fileName}`);
  return fromJar(body, url);
}

async function findOpenApi() {
  if (process.env.OPENAPI_FILE) {
    return { yaml: readFileSync(process.env.OPENAPI_FILE), origin: process.env.OPENAPI_FILE };
  }
  if (process.env.API_JAR) return fromJar(readFileSync(process.env.API_JAR), process.env.API_JAR);
  if (process.env.MAVEN_REPOSITORY_URL) return downloadJar();
  const localRepository = process.env.MAVEN_LOCAL_REPOSITORY ?? join(homedir(), '.m2', 'repository');
  const localJar = join(localRepository, artifactPath, jarName);
  if (existsSync(localJar)) return fromJar(readFileSync(localJar), localJar);
  if (existsSync(handPlacedCopy)) {
    console.warn(`fetch-openapi: no API jar found, so the copy in ${handPlacedCopy} is used. It may be older than the API.`);
    return { yaml: readFileSync(handPlacedCopy), origin: handPlacedCopy };
  }
  throw new Error([
    `found no API jar ${config.apiJar}. Give one of these:`,
    '- run `mvn install` in the backend, which puts the jar into the local Maven repository',
    '- API_JAR=<path of the jar>',
    '- MAVEN_REPOSITORY_URL=<repository>, and MAVEN_USERNAME and MAVEN_PASSWORD if it needs a login',
    '- OPENAPI_FILE=<path of an OpenAPI file>',
  ].join('\n'));
}

try {
  const { yaml, origin } = await findOpenApi();
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, yaml);
  console.log(`fetch-openapi: ${fileInJar} from ${origin}`);
} catch (error) {
  console.error(`fetch-openapi: ${error.message}`);
  process.exit(1);
}
