// Two builds, two sets of names. The browser defines the name of a web component only once per
// page, so a new build of the module loads without a reload of the page only if its names are new.
// The names come from src/bc-build.js, which scripts/write-bc-build.mjs writes for every build.
// This runs that script twice and builds names from both files.
//
// The other tests check that the built entries use the names of the file the last build wrote.

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { after, describe, it } from 'node:test';
import { createElementNames } from '@vanillabp/bc-module-kit';

const projectDir = resolve(import.meta.dirname, '..');
const scratch = mkdtempSync(join(tmpdir(), 'bc-build-'));

after(() => rmSync(scratch, { recursive: true, force: true }));

/** Runs scripts/write-bc-build.mjs into a file of its own and imports what it wrote. */
async function writeBcBuild(name) {
  const file = join(scratch, `${name}.js`);
  execFileSync(process.execPath, [join(projectDir, 'scripts', 'write-bc-build.mjs'), file], { stdio: 'ignore' });
  return import(pathToFileURL(file).href);
}

describe('the build id', () => {
  it('two builds give the web components different names', async () => {
    const first = await writeBcBuild('first');
    // BUILD_ID counts milliseconds, so the second build has to start at least one later.
    await new Promise((done) => setTimeout(done, 5));
    const second = await writeBcBuild('second');

    assert.equal(first.WORKFLOW_MODULE_ID, 'loan-approval');
    assert.equal(second.WORKFLOW_MODULE_ID, 'loan-approval');
    assert.match(first.BUILD_ID, /^[a-z0-9]+$/);
    assert.notEqual(first.BUILD_ID, second.BUILD_ID);
    const firstNames = createElementNames(first.WORKFLOW_MODULE_ID, first.BUILD_ID);
    const secondNames = createElementNames(second.WORKFLOW_MODULE_ID, second.BUILD_ID);
    assert.notEqual(firstNames.workflow('loan_approval', 'page'), secondNames.workflow('loan_approval', 'page'));
    assert.ok(firstNames.workflow('loan_approval', 'page').startsWith('loan-approval-'));
  });
});
