// The browser tests: the host of the module kit loads the built micro-frontends in Chromium, the
// way a cockpit loads them. Once as a cockpit with the areas body, information and actions, and
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

const assessRisk = { id: 'task-1', title: 'Assess risk', bpmnProcessId: 'loan_approval', taskDefinition: 'assessRisk' };
const checkDocuments = { id: 'task-2', title: 'Check documents', bpmnProcessId: 'loan_approval', taskDefinition: 'checkDocuments' };

const AREAS = { body: 'body', information: 'information', actions: 'actions' };

/** The two kinds of cockpit: with the areas of the default convention, and with 'all' only. */
const COCKPITS = [
  { name: 'a cockpit with areas', query: '?areas=body,information,actions', areas: ['body', 'information', 'actions'] },
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
 * Answers the module API in the browser. GET returns the data of the form, or `status` if one
 * is given. POST answers 204 and keeps the body it got.
 */
async function mockApi(page, { forms = {}, status } = {}) {
  const completed = [];
  await page.route('**/wm/loan-approval/api/user-tasks/*/*', async (route) => {
    const request = route.request();
    const [, userTaskId, form] = new URL(request.url()).pathname.match(/\/user-tasks\/([^/]+)\/([^/]+)$/);
    if (status) return route.fulfill({ status });
    if (request.method() === 'POST') {
      completed.push({ userTaskId, form, body: request.postDataJSON() });
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: forms[form] });
  });
  return completed;
}

async function openPage(query, api) {
  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const completed = await mockApi(page, api);
  await page.goto(`${server.url}/${query}`);
  await page.waitForFunction(() => window.bcTest !== undefined);
  return { page, pageErrors, completed };
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
    ), [assessRisk, checkDocuments, { ...assessRisk, bpmnProcessId: 'other' }, { ...assessRisk, taskDefinition: 'unknown' }]);
    assert.deepEqual(forms, [
      {
        entry: 'AssessRiskForm',
        element: {
          all: 'loan-approval-assess-risk',
          body: 'loan-approval-assess-risk-body',
          information: 'loan-approval-assess-risk-information',
          actions: 'loan-approval-assess-risk-actions',
        },
      },
      {
        entry: 'CheckDocumentsForm',
        element: {
          all: 'loan-approval-check-documents',
          body: 'loan-approval-check-documents-body',
          information: 'loan-approval-check-documents-information',
          actions: 'loan-approval-check-documents-actions',
        },
      },
      undefined,
      undefined,
    ]);
    await page.close();
  });

  it('UserTaskList shows the risk assessment from the details of a user task', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    const columns = await page.evaluate(async (tasks) => Promise.all(
      tasks.map((task) => window.bcTest.host.ask('loan-approval', 'UserTaskList', [task])),
    ), [checkDocuments, { ...checkDocuments, bpmnProcessId: 'other' }]);
    assert.equal(columns[0].length, 1);
    assert.equal(columns[0][0].path, 'details.riskAssessment');
    assert.deepEqual(columns[0][0].title, { en: 'Risk assessment', de: 'Risikobewertung' });
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
    assert.ok(loaded.includes(`/wm/loan-approval/${entries.UserTaskList}`), loaded.join(', '));
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
    it('"Assess risk" shows the loan request, and "Complete" sends the risk assessment once it is filled', async () => {
      const { page, pageErrors, completed } = await openPage(cockpit.query, {
        forms: { 'assess-risk': { loanRequestId: 'LR-4711', amount: 25000 } },
      });
      assert.deepEqual(await page.evaluate((task) => window.bcTest.show(task), assessRisk), cockpit.areas);

      const elements = await page.evaluate(() => [...document.querySelectorAll('#user-task-page [data-bc-area] > *, #user-task-page > :not([data-bc-area])')]
        .map((element) => [element.localName, element.shadowRoot?.mode]));
      const expected = cockpit.areas.map((area) => [area === 'all' ? 'loan-approval-assess-risk' : `loan-approval-assess-risk-${AREAS[area]}`, 'open']);
      assert.deepEqual(elements, expected);

      await assertText(page.getByTestId('assess-risk-loan-request'), 'LR-4711');
      await assertText(page.getByTestId('assess-risk-amount'), '25,000');
      const complete = page.getByTestId('assess-risk-complete');
      assert.equal(await complete.isDisabled(), true);
      await page.getByTestId('assess-risk-input').fill('   ');
      assert.equal(await complete.isDisabled(), true);
      await page.getByTestId('assess-risk-input').fill('low, a steady income');
      // The styles of the page do not reach the button in the shadow DOM.
      assert.notEqual(await complete.evaluate((button) => getComputedStyle(button).backgroundColor), 'rgb(255, 0, 0)');
      // click() waits until the button is enabled. That only happens when 'actions' got the value.
      await complete.click({ timeout: 5000 });
      await page.getByTestId('assess-risk-done').waitFor();

      assert.deepEqual(completed, [{ userTaskId: 'task-1', form: 'assess-risk', body: { riskAssessment: 'low, a steady income' } }]);
      assert.equal(await page.getByTestId('assess-risk-input').isDisabled(), true);
      assert.equal(await complete.isDisabled(), true);
      assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), [{ level: 'success', text: 'Completed', sticky: false }]);

      // Only the open form was loaded.
      const loaded = await loadedFiles(page);
      assert.ok(loaded.some((path) => path.includes('/AssessRiskForm-')), loaded.join(', '));
      assert.ok(!loaded.some((path) => path.includes('/CheckDocumentsForm-')), loaded.join(', '));
      assert.deepEqual(await page.evaluate(() => window.bcTest.errors), []);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('"Check documents" shows the risk assessment read-only, and "Complete" sends the note', async () => {
      const { page, pageErrors, completed } = await openPage(cockpit.query, {
        forms: { 'check-documents': { loanRequestId: 'LR-4711', riskAssessment: 'low, a steady income' } },
      });
      assert.deepEqual(await page.evaluate((task) => window.bcTest.show(task), checkDocuments), cockpit.areas);

      await assertText(page.getByTestId('check-documents-loan-request'), 'LR-4711');
      await assertText(page.getByTestId('check-documents-risk'), 'low, a steady income');
      assert.equal(await page.getByTestId('check-documents-risk').evaluate((element) => element.localName), 'dd');
      await page.getByTestId('check-documents-input').fill('all documents are there');
      await page.getByTestId('check-documents-complete').click();
      await page.getByTestId('check-documents-done').waitFor();

      assert.deepEqual(completed, [{ userTaskId: 'task-2', form: 'check-documents', body: { documentsNote: 'all documents are there' } }]);
      const loaded = await loadedFiles(page);
      assert.ok(!loaded.some((path) => path.includes('/AssessRiskForm-')), loaded.join(', '));
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a user task the module does not know any more shows a message instead of the form', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { status: 404 });
      await page.evaluate((task) => window.bcTest.show(task), assessRisk);
      await assertText(page.getByTestId('assess-risk-error'), 'This user task is not open any more.');
      assert.equal(await page.getByTestId('assess-risk-input').count(), 0);
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
