// Builds the micro-frontends of the module into dist/micro-frontends: one ES module per entry,
// with the hash of its content in the file name and no bare import, and bc-manifest.json, which
// tells the cockpit which file holds which entry.
//
// `npm run compile` runs before. It is the Angular compiler, ngc: it checks the types and the
// templates and writes JavaScript into build/ngc. esbuild bundles that JavaScript here. Angular's
// own packages come partly compiled, so the Angular linker finishes them while esbuild loads them.
// That is the same work the Angular CLI does, without its application builder, which makes one
// application and not one ES module per entry.
//
// The two forms share one chunk with Angular, so the browser loads Angular once for this module.
// The data entries never import it. `npm run check:entries` proves that afterwards.

import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { transformAsync } from '@babel/core';
import { ConsoleLogger, LogLevel, NodeJSFileSystem } from '@angular/compiler-cli';
import { needsLinking } from '@angular/compiler-cli/linker';
import { createEs2015LinkerPlugin } from '@angular/compiler-cli/linker/babel';
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

/** Entry name -> file written by ngc from src. The data entries have the names the kit fixes. */
const entries = {
  [USER_TASK_FORM]: 'build/ngc/data/user-task-form.js',
  [USER_TASK_LIST]: 'build/ngc/data/user-task-list.js',
  AssessRiskForm: 'build/ngc/forms/assess-risk-form.js',
  CheckDocumentsForm: 'build/ngc/forms/check-documents-form.js',
};

/** Finishes the partly compiled code of Angular's packages, which needs the linker. */
const linkerPlugin = createEs2015LinkerPlugin({ fileSystem: new NodeJSFileSystem(), logger: new ConsoleLogger(LogLevel.warn), linkerJitMode: false });
const angularLinker = {
  name: 'angular-linker',
  setup(esbuild) {
    esbuild.onLoad({ filter: /[\\/]node_modules[\\/]@angular[\\/].+\.m?js$/ }, async ({ path }) => {
      const source = await readFile(path, 'utf8');
      if (!needsLinking(path, source)) return { contents: source, loader: 'js' };
      const linked = await transformAsync(source, { filename: path, plugins: [linkerPlugin], configFile: false, babelrc: false, compact: false, sourceMaps: false });
      return { contents: linked.code, loader: 'js' };
    });
  },
};

/**
 * The path aliases of tsconfig.json, such as '@shared', for the JavaScript which ngc wrote. ngc
 * keeps such an import as it is, and the alias points to the TypeScript file in src. Here it
 * points to the JavaScript file which ngc wrote from it, in build/ngc.
 */
const { paths } = JSON.parse(readFileSync(join(projectDir, 'tsconfig.json'), 'utf8')).compilerOptions;
const compiled = Object.fromEntries(Object.entries(paths)
  .map(([alias, [file]]) => [alias, join(projectDir, file.replace(/^\.\/src\//, 'build/ngc/').replace(/\.ts$/, '.js'))]));
const pathAliases = {
  name: 'path-aliases',
  setup(esbuild) {
    esbuild.onResolve({ filter: /^@[a-z-]+$/ }, ({ path }) => (compiled[path] ? { path: compiled[path] } : undefined));
    // src/bc-build.js is JavaScript already, so ngc does not copy it to build/ngc. The import of
    // it goes to the file in src, which scripts/write-bc-build.mjs wrote for this build.
    esbuild.onResolve({ filter: /^\.\/bc-build\.js$/ }, () => ({ path: join(projectDir, 'src', 'bc-build.js') }));
  },
};

rmSync(outdir, { recursive: true, force: true });
mkdirSync(outdir, { recursive: true });
mkdirSync(join(projectDir, 'build'), { recursive: true });

const entryPoints = Object.fromEntries(Object.entries(entries).map(([name, source]) => [name, join(projectDir, source)]));
const bundle = await build({
  entryPoints,
  absWorkingDir: projectDir,
  outdir,
  bundle: true,
  splitting: true,
  format: 'esm',
  target: 'es2022',
  minify: true,
  metafile: true,
  entryNames: 'entries/[name]-[hash]',
  chunkNames: 'chunks/[name]-[hash]',
  platform: 'browser',
  // Angular reads these to leave out its checks for development and its runtime compiler.
  define: { ngDevMode: 'false', ngJitMode: 'false', ngI18nClosureMode: 'false', ngServerMode: 'false' },
  plugins: [pathAliases, angularLinker],
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
