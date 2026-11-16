// Builds the dev shell into dist/dev-shell: index.html and one bundle with React, the host of the
// module kit and the client of the process driver. It is not part of the UI jar or the image.
// `npm run start` serves it, and the browser tests load it.

import { copyFileSync, mkdirSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { build } from 'esbuild';

const projectDir = resolve(import.meta.dirname, '..');
const outdir = join(projectDir, 'dist', 'dev-shell');

rmSync(outdir, { recursive: true, force: true });
mkdirSync(outdir, { recursive: true });
await build({
  entryPoints: { main: join(projectDir, 'dev-shell', 'src', 'main.tsx') },
  absWorkingDir: projectDir,
  outdir,
  bundle: true,
  format: 'esm',
  target: 'es2022',
  sourcemap: true,
  jsx: 'automatic',
  define: { 'process.env.NODE_ENV': '"development"' },
  logLevel: 'warning',
});
copyFileSync(join(projectDir, 'dev-shell', 'index.html'), join(outdir, 'index.html'));
console.log('build-dev-shell: dist/dev-shell');
