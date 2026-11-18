// Writes src/bc-build.js with the two values the names of the web components are built from:
//
// - WORKFLOW_MODULE_ID, from `config.workflowModuleId` in package.json. It has to be the id the
//   module registers with at the cockpit, or the cockpit refuses every name of the module.
// - BUILD_ID, the time of this build in milliseconds, in base 36. Every build gets new names. The
//   browser defines a name only once per page, so with new names a new build of the module loads
//   while somebody has the cockpit open, without a reload of the page.
//
// scripts/build-micro-frontends.mjs reads the time back from BUILD_ID and passes it to
// createManifest, so the manifest and the names speak of one build. `npm run build` runs this
// script before the type check. The file is written for every build and not checked in, see
// .gitignore. Its types are in src/bc-build.d.ts.
//
// Usage: node scripts/write-bc-build.mjs [<file>]   (src/bc-build.js if no file is given)

import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const projectDir = resolve(import.meta.dirname, '..');
const target = resolve(projectDir, process.argv[2] ?? join('src', 'bc-build.js'));
const { config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const buildTime = new Date();
const buildId = buildTime.getTime().toString(36);

writeFileSync(target, [
  '// Written by scripts/write-bc-build.mjs for every build. Not checked in.',
  `export const WORKFLOW_MODULE_ID = ${JSON.stringify(config.workflowModuleId)};`,
  `export const BUILD_ID = '${buildId}';`,
  '',
].join('\n'));
console.log(`write-bc-build: module ${config.workflowModuleId}, build ${buildId} (${buildTime.toISOString()})`);
