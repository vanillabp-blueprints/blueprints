// The browser tests of the forms: the host of the module kit loads the built micro-frontends in
// Chromium, the way a cockpit loads them. Once as a cockpit with the areas body and actions, and
// once as a cockpit with the minimum only, the area 'all'. The module API is answered in the
// browser, at the level of the network, so the forms run unchanged.
//
// Run `npm run build` before; the tests read dist/micro-frontends.

import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { after, before, describe, it } from 'node:test';
import { build } from 'esbuild';
import { chromium } from 'playwright';
import { findFrameworkCode } from '@vanillabp/bc-module-kit/check';
import { startServer } from './server.js';

const projectDir = resolve(import.meta.dirname, '..', '..');
const microFrontends = join(projectDir, 'dist', 'micro-frontends');
const hostBundle = join(projectDir, 'build', 'test-host.js');

const details = { amount: 25000, creditScore: 720 };
const assessRisk = { id: 'task-1', businessId: 'LR-4711', title: { en: 'Assess risk' }, bpmnProcessId: 'loan_approval', taskDefinition: 'assessRisk', details };
const checkContract = { id: 'task-2', businessId: 'LR-4711', title: { en: 'Check contract' }, bpmnProcessId: 'loan_approval', taskDefinition: 'checkContract', details };

/** The two kinds of cockpit: with the areas body and actions, and with 'all' only. */
const COCKPITS = [
  { name: 'a cockpit with areas', query: '?areas=body,actions', areas: ['body', 'actions'] },
  { name: 'a cockpit with the area all only', query: '', areas: ['all'] },
];

let server;
let browser;

before(async () => {
  if (!existsSync(join(microFrontends, 'bc-manifest.json'))) {
    throw new Error('dist/micro-frontends/bc-manifest.json is missing. Run `npm run build` before `npm test`.');
  }
  await build({ entryPoints: [join(import.meta.dirname, 'page.js')], bundle: true, format: 'esm', outfile: hostBundle, logLevel: 'warning' });
  server = await startServer({
    files: { '/': join(import.meta.dirname, 'index.html'), '/host.js': hostBundle },
    folders: { '/wm/loan-approval/': microFrontends },
  });
  browser = await chromium.launch(process.env.BC_CHROMIUM_PATH ? { executablePath: process.env.BC_CHROMIUM_PATH } : {});
});

after(async () => {
  await browser?.close();
  await server?.close();
});

/**
 * Answers the module API in the browser. A call which completes a user task answers with the
 * next status of `statuses`, 200 once they are used up, and is written down.
 */
async function mockApi(page, statuses = []) {
  const calls = [];
  await page.route('**/wm/loan-approval/api/loan-approval/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    calls.push(`${route.request().method()} ${pathname}`);
    const status = statuses.shift() ?? 200;
    return route.fulfill({ status, contentType: 'text/plain', body: status === 409 ? 'not signed yet' : 'done' });
  });
  return calls;
}

async function openPage(query, statuses) {
  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const calls = await mockApi(page, statuses);
  await page.goto(`${server.url}/${query}`);
  await page.waitForFunction(() => window.bcTest !== undefined);
  return { page, pageErrors, calls };
}

/** The paths below /wm/loan-approval/ the page has loaded so far. */
const loadedFiles = (page) => page.evaluate(() => performance.getEntriesByType('resource')
  .map(({ name }) => new URL(name).pathname)
  .filter((path) => path.startsWith('/wm/loan-approval/') && path.endsWith('.js')));

const manifest = () => JSON.parse(readFileSync(join(microFrontends, 'bc-manifest.json'), 'utf8'));

describe('the data entries', () => {
  it('UserTaskForm names the visual entry and the web components of each user task', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    const forms = await page.evaluate(async (tasks) => Promise.all(
      tasks.map((task) => window.bcTest.host.ask('loan-approval', 'UserTaskForm', [task])),
    ), [assessRisk, checkContract, { ...assessRisk, bpmnProcessId: 'other' }, { ...assessRisk, taskDefinition: 'unknown' }]);
    assert.deepEqual(forms, [
      {
        entry: 'AssessRiskForm',
        element: { all: 'loan-approval-assess-risk', body: 'loan-approval-assess-risk-body', actions: 'loan-approval-assess-risk-actions' },
      },
      {
        entry: 'CheckContractForm',
        element: { all: 'loan-approval-check-contract', body: 'loan-approval-check-contract-body', actions: 'loan-approval-check-contract-actions' },
      },
      undefined,
      undefined,
    ]);
    await page.close();
  });

  it('UserTaskList shows the amount and the credit score from the details of a user task', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    const columns = await page.evaluate(async (tasks) => Promise.all(
      tasks.map((task) => window.bcTest.host.ask('loan-approval', 'UserTaskList', [task])),
    ), [checkContract, { ...checkContract, bpmnProcessId: 'other' }]);
    assert.deepEqual(columns[0].map(({ path }) => path), ['details.amount', 'details.creditScore']);
    assert.equal(columns[1], undefined);
    await page.close();
  });

  it('asking the data entries loads neither a form nor React', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    await page.evaluate(async (task) => {
      await window.bcTest.host.ask('loan-approval', 'UserTaskForm', [task]);
      await window.bcTest.host.ask('loan-approval', 'UserTaskList', [task]);
    }, assessRisk);
    const loaded = await loadedFiles(page);
    const { entries } = manifest();
    assert.ok(loaded.includes(`/wm/loan-approval/${entries.UserTaskForm}`), loaded.join(', '));
    assert.ok(!loaded.some((path) => path.includes('Form-') && !path.includes('UserTaskForm')), loaded.join(', '));
    for (const path of loaded) {
      const source = readFileSync(join(microFrontends, path.slice('/wm/loan-approval/'.length)), 'utf8');
      assert.deepEqual(findFrameworkCode(source), [], path);
    }
    await page.close();
  });
});

for (const cockpit of COCKPITS) {
  describe(`the forms in ${cockpit.name}`, () => {
    it('"Assess risk" shows the loan request, and its button completes the task', async () => {
      const { page, pageErrors, calls } = await openPage(cockpit.query);
      assert.deepEqual(await page.evaluate((task) => window.bcTest.show(task), assessRisk), cockpit.areas);

      await assertText(page.getByTestId('assess-risk-loan-request'), 'LR-4711');
      await assertText(page.getByTestId('assess-risk-amount'), '25,000');
      await assertText(page.getByTestId('assess-risk-credit-score'), '720');
      const complete = page.getByTestId('assess-risk-complete');
      // The styles of the page do not reach the button in the shadow DOM.
      assert.notEqual(await complete.evaluate((button) => getComputedStyle(button).backgroundColor), 'rgb(255, 0, 0)');
      await complete.click();
      await page.getByTestId('assess-risk-done').waitFor();

      assert.deepEqual(calls, ['GET /wm/loan-approval/api/loan-approval/LR-4711/assess-risk/task-1']);
      assert.equal(await complete.isDisabled(), true);
      assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), [{ level: 'success', text: 'Completed', sticky: false }]);
      const loaded = await loadedFiles(page);
      assert.ok(!loaded.some((path) => path.includes('/CheckContractForm-')), loaded.join(', '));
      assert.deepEqual(await page.evaluate(() => window.bcTest.errors), []);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('"Check contract" says that the signature is missing, keeps the task open and completes it later', async () => {
      const { page, pageErrors, calls } = await openPage(cockpit.query, [409]);
      await page.evaluate((task) => window.bcTest.show(task), checkContract);

      const complete = page.getByTestId('check-contract-complete');
      await complete.click();
      await assertText(page.getByTestId('check-contract-error'), 'The customer has not signed the contract yet. Please try again later.');
      assert.equal(await complete.isDisabled(), false);

      await complete.click();
      await page.getByTestId('check-contract-done').waitFor();
      assert.equal(await page.getByTestId('check-contract-error').count(), 0);
      assert.deepEqual(calls, [
        'GET /wm/loan-approval/api/loan-approval/LR-4711/check-contract/task-2',
        'GET /wm/loan-approval/api/loan-approval/LR-4711/check-contract/task-2',
      ]);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a module which does not answer shows a message, and the button stays enabled', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, [500]);
      await page.evaluate((task) => window.bcTest.show(task), assessRisk);
      await page.getByTestId('assess-risk-complete').click();
      await assertText(page.getByTestId('assess-risk-error'), 'The workflow module did not answer. Please try again.');
      assert.equal(await page.getByTestId('assess-risk-complete').isDisabled(), false);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });
  });
}

/** Waits until a locator shows a text, at most five seconds. */
async function assertText(locator, expected) {
  try {
    await locator.filter({ hasText: expected }).waitFor({ timeout: 5000 });
  } catch {
    assert.equal(await locator.textContent(), expected);
  }
}
