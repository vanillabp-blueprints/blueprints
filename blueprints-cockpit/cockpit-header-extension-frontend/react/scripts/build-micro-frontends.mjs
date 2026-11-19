// Builds the micro-frontends of the module into dist/micro-frontends: one ES module per entry,
// with the hash of its content in the file name and no bare import, and bc-manifest.json, which
// tells the cockpit which file holds which entry.
//
// The start form is the one visual entry. React goes into a chunk of its own next to it.
// The data entry Header never imports it. `npm run check:entries --data-entry Header` proves that
// afterwards. Header is not one of the four entries the kit names, so the name is written here.
//
// React keeps the name of its chunk from build to build, so a browser takes it from its cache after
// a new build of the module. FRAMEWORK below says how; test/framework-chunk.test.js proves it.
//
// Usage: node scripts/build-micro-frontends.mjs [<outdir> <metafile>]
//        (dist/micro-frontends and build/meta.json if not given; the test builds elsewhere)

import { mkdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import { createManifest } from '@vanillabp/bc-module-kit';

const projectDir = resolve(import.meta.dirname, '..');
const outdir = resolve(process.argv[2] ?? join(projectDir, 'dist', 'micro-frontends'));
const metafilePath = resolve(process.argv[3] ?? join(projectDir, 'build', 'meta.json'));
const { version } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
// The time of this build. scripts/write-bc-build.mjs wrote it into src/bc-build.js as BUILD_ID, the
// names of the web components carry it, and the manifest gets the same time. So the manifest and
// the names speak of one build.
const { BUILD_ID } = await import(pathToFileURL(join(projectDir, 'src', 'bc-build.js')).href);
const buildTime = new Date(Number.parseInt(BUILD_ID, 36));

/**
 * Entry name -> source file. The data entry Header has the name the skill proposes for the header
 * of a cockpit. A cockpit with a header asks it, any other cockpit ignores it.
 */
const entries = {
  Header: 'src/data/header.ts',
  StartForm: 'src/start-form/start-form.tsx',
};

/**
 * React is CommonJS, so the chunk with React calls two helpers of esbuild. esbuild puts its helpers
 * into the chunk which every entry imports. Without more, that chunk also holds src/bc-build.js,
 * which is new for every build. The hash in a chunk's name covers the names of the chunks it
 * imports, so React would get a new name with every build and the browser would load it again.
 *
 * So the build gets one more entry, FRAMEWORK, which imports React and nothing of this module.
 * Now React is a chunk of its own, the chunk which every entry imports holds the helpers of esbuild
 * and nothing else, and src/bc-build.js goes into a chunk which the chunk with React does not
 * import. A new build changes the names of the entries and of that one small chunk only. The extra
 * entry is deleted after the build. No chunk imports an entry, so nothing refers to it.
 */
const FRAMEWORK = 'bc-framework';
/** What the code of this module imports React from. JSX imports react/jsx-runtime. */
const frameworkImports = ['react', 'react/jsx-runtime', 'react-dom/client'];
const frameworkEntry = {
  name: FRAMEWORK,
  setup(builder) {
    builder.onResolve({ filter: new RegExp(`^${FRAMEWORK}$`) }, () => ({ path: FRAMEWORK, namespace: FRAMEWORK }));
    builder.onLoad({ filter: /.*/, namespace: FRAMEWORK }, () => ({
      contents: frameworkImports.map((source) => `import ${JSON.stringify(source)};`).join('\n'),
      resolveDir: projectDir,
      loader: 'js',
    }));
  },
};

rmSync(outdir, { recursive: true, force: true });
mkdirSync(outdir, { recursive: true });
mkdirSync(dirname(metafilePath), { recursive: true });

const entryPoints = Object.fromEntries(Object.entries(entries).map(([name, source]) => [name, join(projectDir, source)]));
const bundle = await build({
  entryPoints: { ...entryPoints, [FRAMEWORK]: FRAMEWORK },
  plugins: [frameworkEntry],
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
  if (entryPoint === `${FRAMEWORK}:${FRAMEWORK}`) {
    unlinkSync(join(projectDir, output));
    delete bundle.metafile.outputs[output];
    continue;
  }
  const name = Object.keys(entryPoints).find((key) => join(projectDir, entryPoint) === entryPoints[key]);
  if (name) files[name] = relative(outdir, join(projectDir, output)).split('\\').join('/');
}
const manifest = createManifest({ entries: files, buildVersion: version, buildTimestamp: buildTime });
writeFileSync(join(outdir, 'bc-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
// The list of inputs goes next to the build, not into it: bc-check-entries reads it, the cockpit does not.
writeFileSync(metafilePath, JSON.stringify(bundle.metafile));

for (const [entry, file] of Object.entries(files)) console.log(`${entry}: ${file}`);
