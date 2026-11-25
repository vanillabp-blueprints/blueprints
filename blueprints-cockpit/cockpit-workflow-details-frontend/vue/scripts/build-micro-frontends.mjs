// Builds the micro-frontends of the module into dist/micro-frontends: one ES module per entry,
// with the hash of its content in the file name and no bare import, and bc-manifest.json, which
// tells the cockpit which file holds which entry.
//
// The components are Vue single-file components, named *.ce.vue as in a Vite project which builds
// web components. The Vue compiler, which comes with the package vue as vue/compiler-sfc, turns
// each one into TypeScript while esbuild loads it. That is the work @vitejs/plugin-vue does for
// Vite, in its mode for web components, in a few lines and without another dependency.
// `npm run typecheck` checks the types of the components before, with vue-tsc.
//
// A <style> block of a *.ce.vue file becomes the list `styles` of the component, as in that mode
// of @vitejs/plugin-vue. When the component is set up, src/shared/vue-element.ts puts the list
// into the shadow root the component draws in. A <style> block of a plain .vue file would have to
// go into the <head> of the cockpit's page, where it does not reach a shadow root, so the build
// fails on it.
//
// The case page is the one visual entry. Vue goes into a chunk of its own next to it.
// The data entries never import it. `npm run check:entries` proves that afterwards.
//
// Vue keeps the name of its chunk from build to build, so a browser takes it from its cache after
// a new build of the module. FRAMEWORK below says how; test/framework-chunk.test.js proves it.
//
// Usage: node scripts/build-micro-frontends.mjs [<outdir> <metafile>]
//        (dist/micro-frontends and build/meta.json if not given; the test builds elsewhere)

import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { dirname, join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import { compileScript, compileStyle, parse } from 'vue/compiler-sfc';
import { createManifest, WORKFLOW_LIST, WORKFLOW_PAGE } from '@vanillabp/bc-module-kit';

const projectDir = resolve(import.meta.dirname, '..');
const outdir = resolve(process.argv[2] ?? join(projectDir, 'dist', 'micro-frontends'));
const metafilePath = resolve(process.argv[3] ?? join(projectDir, 'build', 'meta.json'));
const { version } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
// The time of this build. scripts/write-bc-build.mjs wrote it into src/bc-build.js as BUILD_ID, the
// names of the web components carry it, and the manifest gets the same time. So the manifest and
// the names speak of one build.
const { BUILD_ID } = await import(pathToFileURL(join(projectDir, 'src', 'bc-build.js')).href);
const buildTime = new Date(Number.parseInt(BUILD_ID, 36));

/** Entry name -> source file. The data entries have the names the kit fixes. */
const entries = {
  [WORKFLOW_PAGE]: 'src/data/workflow-page.ts',
  [WORKFLOW_LIST]: 'src/data/workflow-list.ts',
  LoanApprovalPage: 'src/pages/loan-approval-page.ts',
};

/**
 * With one visual entry, esbuild would put Vue into the file of that entry. That file imports the
 * chunk with src/bc-build.js, which is new for every build. The hash in a file's name covers the
 * names of the chunks it imports, so Vue would get a new name with every build, and the browser
 * would load it again.
 *
 * So the build gets one more entry, FRAMEWORK, which imports Vue and nothing of this module. Now
 * two entries share Vue, and esbuild puts it into a chunk of its own, which imports nothing. A new
 * build changes the names of the entries and of the chunk with src/bc-build.js only. The extra
 * entry is deleted after the build. No chunk imports an entry, so nothing refers to it.
 */
const FRAMEWORK = 'bc-framework';
/** What the code of this module imports Vue from. */
const frameworkImports = ['vue'];
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

/** The file whose useComponentStyles() puts the styles of a component into its shadow root. */
const vueElement = join(projectDir, 'src', 'shared', 'vue-element.ts');

/**
 * Compiles a single-file component into TypeScript, with its template as a render function. The
 * styles of a *.ce.vue file become the list `styles` of the component.
 */
const vueComponents = {
  name: 'vue-components',
  setup(esbuild) {
    esbuild.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
      const { descriptor, errors } = parse(await readFile(path, 'utf8'), { filename: path });
      if (errors.length > 0) return { errors: errors.map((error) => ({ text: String(error.message ?? error) })) };
      const customElement = path.endsWith('.ce.vue');
      if (descriptor.styles.length > 0 && !customElement) {
        return { errors: [{ text: 'A component with a <style> block must be named *.ce.vue, so its styles go into the shadow root.' }] };
      }
      const id = createHash('sha256').update(relative(projectDir, path)).digest('hex').slice(0, 8);
      const script = compileScript(descriptor, { id, inlineTemplate: true, isProd: true, genDefaultAs: '_sfc_main' });
      const styles = [];
      for (const style of descriptor.styles) {
        if (style.lang) return { errors: [{ text: `<style lang="${style.lang}"> is not supported, write plain CSS.` }] };
        const css = compileStyle({ source: style.content, filename: path, id: `data-v-${id}`, scoped: style.scoped, isProd: true });
        if (css.errors.length > 0) return { errors: css.errors.map((error) => ({ text: String(error.message ?? error) })) };
        styles.push(css.code);
      }
      const contents = styles.length === 0
        ? `${script.content}\nexport default _sfc_main;\n`
        : [
          script.content,
          `import { useComponentStyles as _useComponentStyles } from ${JSON.stringify(vueElement)};`,
          `_sfc_main.styles = ${JSON.stringify(styles)};`,
          'const _sfc_setup = _sfc_main.setup;',
          '_sfc_main.setup = (props, context) => { _useComponentStyles(_sfc_main); return _sfc_setup(props, context); };',
          'export default _sfc_main;',
          '',
        ].join('\n');
      return { contents, loader: 'ts', resolveDir: join(path, '..') };
    });
  },
};

rmSync(outdir, { recursive: true, force: true });
mkdirSync(outdir, { recursive: true });
mkdirSync(dirname(metafilePath), { recursive: true });

const entryPoints = Object.fromEntries(Object.entries(entries).map(([name, source]) => [name, join(projectDir, source)]));
const bundle = await build({
  entryPoints: { ...entryPoints, [FRAMEWORK]: FRAMEWORK },
  plugins: [frameworkEntry, vueComponents],
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
  // Vue reads these to leave out its checks for development, the Options API, which the
  // components here do not use, and the code for its browser tools.
  define: {
    'process.env.NODE_ENV': '"production"',
    __VUE_OPTIONS_API__: 'false',
    __VUE_PROD_DEVTOOLS__: 'false',
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false',
  },
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
