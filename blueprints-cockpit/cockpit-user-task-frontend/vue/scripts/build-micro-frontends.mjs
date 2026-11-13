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
// The two forms share one chunk with Vue, so the browser loads Vue once for this module. The data
// entries never import it. `npm run check:entries` proves that afterwards.

import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { join, relative, resolve } from 'node:path';
import { build } from 'esbuild';
import { compileScript, compileStyle, parse } from 'vue/compiler-sfc';
import { createManifest, USER_TASK_FORM, USER_TASK_LIST } from '@vanillabp/bc-module-kit';

const projectDir = resolve(import.meta.dirname, '..');
const outdir = join(projectDir, 'dist', 'micro-frontends');
const metafilePath = join(projectDir, 'build', 'meta.json');
const { version } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));

/** Entry name -> source file. The data entries have the names the kit fixes. */
const entries = {
  [USER_TASK_FORM]: 'src/data/user-task-form.ts',
  [USER_TASK_LIST]: 'src/data/user-task-list.ts',
  AssessRiskForm: 'src/forms/assess-risk-form.ts',
  CheckDocumentsForm: 'src/forms/check-documents-form.ts',
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
  // Vue reads these to leave out its checks for development, the Options API, which the
  // components here do not use, and the code for its browser tools.
  define: {
    'process.env.NODE_ENV': '"production"',
    __VUE_OPTIONS_API__: 'false',
    __VUE_PROD_DEVTOOLS__: 'false',
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false',
  },
  plugins: [vueComponents],
  logLevel: 'warning',
});

// The manifest: entry name -> file, from esbuild's own list of outputs.
const files = {};
for (const [output, { entryPoint }] of Object.entries(bundle.metafile.outputs)) {
  if (!entryPoint) continue;
  const name = Object.keys(entryPoints).find((key) => join(projectDir, entryPoint) === entryPoints[key]);
  if (name) files[name] = relative(outdir, join(projectDir, output)).split('\\').join('/');
}
const manifest = createManifest({ entries: files, buildVersion: version, buildTimestamp: new Date() });
writeFileSync(join(outdir, 'bc-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
// The list of inputs goes next to the build, not into it: bc-check-entries reads it, the cockpit does not.
writeFileSync(metafilePath, JSON.stringify(bundle.metafile));

for (const [entry, file] of Object.entries(files)) console.log(`${entry}: ${file}`);
