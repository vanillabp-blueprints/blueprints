// Writes bc-manifest.json into dist/micro-frontends, which tells the cockpit which file holds
// which entry. `ng build` runs before and writes one ES module per entry, see src/entries.ts.
//
// The list of outputs comes from the Angular CLI itself: with `statsJson` in angular.json it writes
// browser-stats.json, the metafile of esbuild, which the CLI uses inside. This script moves that file
// to build/meta.json, because bc-check-entries reads it and the cockpit does not.
//
// The CLI also writes main-<hash>.js, the build of src/entries.ts. The cockpit never loads it, and
// no other file imports it, so this script deletes it. The CLI has no option to leave it out.

import { readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createManifest, USER_TASK_FORM, USER_TASK_LIST } from '@vanillabp/bc-module-kit';

const projectDir = resolve(import.meta.dirname, '..');
const outdir = join(projectDir, 'dist', 'micro-frontends');
const metafilePath = join(projectDir, 'build', 'meta.json');
const { version } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
// The time of this build. scripts/write-bc-build.mjs wrote it into src/bc-build.js as BUILD_ID, the
// names of the web components carry it, and the manifest gets the same time. So the manifest and
// the names speak of one build.
const { BUILD_ID } = await import(pathToFileURL(join(projectDir, 'src', 'bc-build.js')).href);
const buildTime = new Date(Number.parseInt(BUILD_ID, 36));

/** Entry name -> source file, the same as in src/entries.ts. The data entries have the names the kit fixes. */
const entries = {
  [USER_TASK_FORM]: 'src/data/user-task-form.ts',
  [USER_TASK_LIST]: 'src/data/user-task-list.ts',
  AssessRiskForm: 'src/forms/assess-risk-form.ts',
  CheckDocumentsForm: 'src/forms/check-documents-form.ts',
};

const metafile = JSON.parse(readFileSync(join(outdir, 'browser-stats.json'), 'utf8'));
const { outputs } = metafile;
const BROWSER_ENTRY = 'src/entries.ts';
for (const [output, { entryPoint }] of Object.entries(outputs)) {
  if (entryPoint !== BROWSER_ENTRY) continue;
  const importers = Object.keys(outputs).filter((other) => outputs[other].imports.some(({ path }) => path === output));
  if (importers.length > 0) throw new Error(`write-manifest: ${importers.join(', ')} import ${output}, so it cannot be deleted.`);
  unlinkSync(join(outdir, output));
  delete outputs[output];
}
writeFileSync(metafilePath, JSON.stringify(metafile));
unlinkSync(join(outdir, 'browser-stats.json'));

const files = {};
for (const [output, { entryPoint }] of Object.entries(outputs)) {
  const name = Object.keys(entries).find((key) => entries[key] === entryPoint);
  // The CLI names each output relative to dist/micro-frontends, as the manifest does.
  if (name) files[name] = output;
}
const missing = Object.keys(entries).filter((name) => !files[name]);
if (missing.length > 0) {
  throw new Error(`write-manifest: ng build wrote no file for ${missing.join(', ')}. Is each of them imported in src/entries.ts?`);
}
const manifest = createManifest({ entries: files, buildVersion: version, buildTimestamp: buildTime });
writeFileSync(join(outdir, 'bc-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

for (const [entry, file] of Object.entries(files)) console.log(`${entry}: ${file}`);
