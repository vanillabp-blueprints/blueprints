// Two builds without a change in the sources: the chunk with Vue has the same name in both, and
// so has the chunk with bpmn-js. The file names are what the browser caches, so with the same
// names a browser takes Vue and bpmn-js from its cache after a new build of the module. The chunks
// they import must keep their names too, because a cached file imports by the names it was built
// with.
//
// This writes src/bc-build.js twice, builds each time into a folder of its own and puts the old
// src/bc-build.js back afterwards, so dist/micro-frontends and src/bc-build.js still match for the
// browser tests. Run `npm run build` before, it generates the API client the build needs.

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join, resolve } from 'node:path';
import { after, before, describe, it } from 'node:test';

const projectDir = resolve(import.meta.dirname, '..');
const bcBuildFile = join(projectDir, 'src', 'bc-build.js');
const bcBuildBefore = existsSync(bcBuildFile) ? readFileSync(bcBuildFile, 'utf8') : undefined;
const scratch = mkdtempSync(join(tmpdir(), 'framework-chunk-'));

after(() => {
  if (bcBuildBefore === undefined) rmSync(bcBuildFile, { force: true });
  else writeFileSync(bcBuildFile, bcBuildBefore);
  rmSync(scratch, { recursive: true, force: true });
});

/** Writes a new src/bc-build.js and builds into a folder of its own. Returns esbuild's metafile. */
function buildOnce(name) {
  const outdir = join(scratch, name);
  const metafile = join(scratch, `${name}-meta.json`);
  execFileSync(process.execPath, [join(projectDir, 'scripts', 'write-bc-build.mjs')], { stdio: 'ignore' });
  execFileSync(process.execPath, [join(projectDir, 'scripts', 'build-micro-frontends.mjs'), outdir, metafile], { stdio: 'ignore' });
  return JSON.parse(readFileSync(metafile, 'utf8'));
}

/**
 * The name of the one output file whose inputs contain `library`, and the names of the files it
 * imports. The chunk with bpmn-js is no entry of the manifest. esbuild still names it in
 * `entryPoint`, because the page imports it with import().
 */
function chunkWith(metafile, library, label) {
  const found = Object.entries(metafile.outputs).filter(([, { inputs }]) =>
    Object.keys(inputs).some((input) => input.includes(library)));
  assert.equal(found.length, 1, `${label} is in one file, found ${found.map(([output]) => output).join(', ')}`);
  const [[output, { imports }]] = found;
  assert.ok(output.includes('/chunks/'), `${label} is in a chunk and not in an entry, found ${output}`);
  return { name: basename(output), imports: imports.map(({ path }) => basename(path)) };
}

let first;
let second;

before(async () => {
  first = buildOnce('first');
  // BUILD_ID counts milliseconds, so the second build has to start at least one later.
  await new Promise((done) => setTimeout(done, 5));
  second = buildOnce('second');
  const names = (metafile) => Object.keys(metafile.outputs).map((output) => basename(output));
  assert.notDeepEqual(names(first), names(second), 'the two builds have different build ids, so some names differ');
});

for (const [label, library] of [['Vue', 'node_modules/@vue/runtime-core/'], ['bpmn-js', 'node_modules/bpmn-js/']]) {
  describe(`the chunk with ${label}`, () => {
    it('keeps its name, and so do the chunks it imports, when the module is built again', () => {
      const firstChunk = chunkWith(first, library, label);
      const secondChunk = chunkWith(second, library, label);
      assert.equal(secondChunk.name, firstChunk.name);
      assert.deepEqual(secondChunk.imports, firstChunk.imports);
    });
  });
}
