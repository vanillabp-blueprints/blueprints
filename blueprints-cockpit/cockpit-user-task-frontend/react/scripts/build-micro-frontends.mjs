// Builds the micro-frontends of the module into dist/micro-frontends: one ES module per entry,
// with the hash of its content in the file name and no bare import, and bc-manifest.json, which
// tells the cockpit which file holds which entry.
//
// The two forms share one chunk with React, so the browser loads React once for this module. The
// data entries never import it. `npm run check:entries` proves that afterwards.

import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
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

/** Entry name -> source file. The data entries have the names the kit fixes. */
const entries = {
  [USER_TASK_FORM]: 'src/data/user-task-form.ts',
  [USER_TASK_LIST]: 'src/data/user-task-list.ts',
  AssessRiskForm: 'src/forms/assess-risk-form.tsx',
  CheckDocumentsForm: 'src/forms/check-documents-form.tsx',
};

rmSync(outdir, { recursive: true, force: true });
mkdirSync(outdir, { recursive: true });
mkdirSync(join(projectDir, 'build'), { recursive: true });

const entryPoints = Object.fromEntries(Object.entries(entries).map(([name, source]) => [name, join(projectDir, source)]));
const bundle = await build({
  entryPoints,
  absWorkingDir: projectDir,
  // The path aliases, such as '@shared', come from the paths of tsconfig.json. So the build, the
  // type check and the editor read them from one place.
  tsconfig: join(projectDir, 'tsconfig.json'),
  outdir,
  bundle: true,
  splitting: true,
  format: 'esm',
  target: 'es2022',
  minify: true,
  metafile: true,
  entryNames: 'entries/[name]-[hash]',
  chunkNames: 'chunks/[name]-[hash]',
  jsx: 'automatic',
  // React reads it to leave out its checks for development.
  define: { 'process.env.NODE_ENV': '"production"' },
  logLevel: 'warning',
});

// The manifest: entry name -> file, from esbuild's own list of outputs.
const files = {};
for (const [output, { entryPoint }] of Object.entries(bundle.metafile.outputs)) {
  if (!entryPoint) continue;
  const name = Object.keys(entryPoints).find((key) => join(projectDir, entryPoint) === entryPoints[key]);
  if (name) files[name] = relative(outdir, join(projectDir, output)).split('\\').join('/');
}
const manifest = createManifest({ entries: files, buildVersion: version, buildTimestamp: buildTime });
writeFileSync(join(outdir, 'bc-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
// The list of inputs goes next to the build, not into it: bc-check-entries reads it, the cockpit does not.
writeFileSync(metafilePath, JSON.stringify(bundle.metafile));

for (const [entry, file] of Object.entries(files)) console.log(`${entry}: ${file}`);
