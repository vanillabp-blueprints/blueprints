// The browser tests of the dev shell: Chromium loads dist/dev-shell and the built
// micro-frontends. There is no application and no simulated cockpit. The tests answer the process
// driver, the official API of the cockpit and the module API in the browser, at the level of the
// network, so the dev shell and the forms run unchanged.
//
// Run `npm run build` before; the tests read dist/dev-shell and dist/micro-frontends.

import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { after, before, describe, it } from 'node:test';
import { build } from 'esbuild';
import { chromium } from 'playwright';
import { startServer } from './server.js';

const projectDir = resolve(import.meta.dirname, '..', '..');
const devShell = join(projectDir, 'dist', 'dev-shell');
const microFrontends = join(projectDir, 'dist', 'micro-frontends');

const STATES = ['ASSESS_RISK_USER_TASK', 'CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE', 'CHECK_CONTRACT_USER_TASK', 'CONTRACT_DECLINED'];
/** The state where the customer declined the contract. The workflow ended, so no user task is open. */
const ENDED = 'CONTRACT_DECLINED';
const details = { amount: 25000, creditScore: 720 };

/** What the simulated cockpit knows once the driver brought a loan request to "Assess risk". */
const workflow = { id: 'wf-1', businessId: 'LR-4711', title: { en: 'Loan approval' } };
const assessRisk = {
  id: 'task-1', workflowId: 'wf-1', businessId: 'LR-4711', title: { en: 'Assess risk' },
  bpmnProcessId: 'loan_approval', taskDefinition: 'assessRisk', details,
};

let server;
let browser;

before(async () => {
  for (const file of [join(devShell, 'index.html'), join(microFrontends, 'bc-manifest.json')]) {
    if (!existsSync(file)) throw new Error(`${file} is missing. Run \`npm run build\` before \`npm test\`.`);
  }
  server = await startServer({
    files: { '/': join(devShell, 'index.html') },
    folders: { '/wm/loan-approval/': microFrontends, '/': devShell },
  });
  browser = await chromium.launch(process.env.BC_CHROMIUM_PATH ? { executablePath: process.env.BC_CHROMIUM_PATH } : {});
});

after(async () => {
  await browser?.close();
  await server?.close();
});

/**
 * Plays the application and the simulated cockpit in the browser.
 *
 * @param driver             'on', or the status the process driver answers every call with
 * @param advance            the status of advance-to, and the reason for one which is not 200
 * @param cockpitKnowsAfter  how many calls the cockpit answers as if it did not know the case yet
 * @returns every call, as 'METHOD path?query', and the bodies the cockpit got
 */
async function playBackend(page, { driver = 'on', advance = { status: 200 }, cockpitKnowsAfter = 0 } = {}) {
  const calls = [];
  const bodies = [];
  let cockpitCalls = 0;
  const record = (route) => {
    const url = new URL(route.request().url());
    calls.push(`${route.request().method()} ${url.pathname}${url.search}`);
    return url;
  };
  // Playwright asks the route registered last first. So the module API comes before the driver,
  // whose paths are below it.
  await page.route('**/wm/loan-approval/api/**', async (route) => {
    record(route);
    return route.fulfill({ contentType: 'text/plain', body: 'done' });
  });
  await page.route('**/wm/loan-approval/api/process-driver/**', async (route) => {
    const url = record(route);
    if (driver !== 'on') return route.fulfill({ status: driver, body: '' });
    if (url.pathname.endsWith('/states')) return route.fulfill({ json: STATES });
    if (advance.status !== 200) return route.fulfill({ status: advance.status, contentType: 'text/plain', body: advance.body });
    const state = url.searchParams.get('state');
    const reached = { state, loanRequestId: 'LR-4711', customerId: 'C-1' };
    return route.fulfill({ json: state === ENDED ? reached : { ...reached, userTaskId: 'task-1' } });
  });
  await page.route('**/official-api/gui/api/v1/**', async (route) => {
    const url = record(route);
    if (route.request().method() === 'POST') bodies.push(route.request().postDataJSON());
    cockpitCalls += 1;
    if (cockpitCalls <= cockpitKnowsAfter) {
      return url.pathname === '/official-api/gui/api/v1/workflow' ? route.fulfill({ json: { workflows: [] } }) : route.fulfill({ status: 404 });
    }
    if (url.pathname === '/official-api/gui/api/v1/workflow') return route.fulfill({ json: { workflows: [workflow] } });
    if (url.pathname === '/official-api/gui/api/v1/workflow/wf-1') return route.fulfill({ json: workflow });
    if (url.pathname === '/official-api/gui/api/v1/workflow/wf-1/usertasks') return route.fulfill({ json: [assessRisk] });
    if (url.pathname === '/official-api/gui/api/v1/usertask/task-1') return route.fulfill({ json: assessRisk });
    return route.fulfill({ status: 404 });
  });
  return { calls, bodies };
}

async function openDevShell(backend, hash = '#/process-driver') {
  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const recorded = await playBackend(page, backend);
  await page.goto(`${server.url}/${hash}`);
  return { page, pageErrors, ...recorded };
}

describe('the page "Process driver"', () => {
  it('lists the states of the backend and starts with test data for every state', async () => {
    const { page, pageErrors } = await openDevShell();
    const state = page.getByTestId('process-driver-state');
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="process-driver-state"] option').length > 0);
    assert.deepEqual(await state.locator('option').allTextContents(), STATES);
    assert.equal(await state.inputValue(), 'ASSESS_RISK_USER_TASK');
    assert.equal(await page.getByTestId('process-driver-amount').inputValue(), '25000');
    assert.equal(await page.getByTestId('process-driver-credit-score').inputValue(), '720');
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('choose a state, the case is created, and the link opens its user task', async () => {
    const { page, pageErrors, calls, bodies } = await openDevShell({ cockpitKnowsAfter: 2 });
    await page.getByTestId('process-driver-state').selectOption('ASSESS_RISK_USER_TASK');
    await page.getByTestId('process-driver-amount').fill('30000');
    await page.getByTestId('process-driver-advance').click();

    await assertText(page.getByTestId('process-driver-loan-request'), 'LR-4711');
    assert.ok(calls.includes('GET /wm/loan-approval/api/process-driver/advance-to?state=ASSESS_RISK_USER_TASK&amount=30000&creditScore=720'), calls.join('\n'));
    // The cockpit learns about the case a moment later, so the dev shell asks again.
    assert.deepEqual(bodies[0], { businessIds: ['LR-4711'], mode: 'All', pageNumber: 0, pageSize: 1 });
    assert.equal(await page.getByTestId('process-driver-open-workflow').getAttribute('href'), '#/workflow/wf-1');
    assert.equal(await page.getByTestId('process-driver-not-in-cockpit').count(), 0);

    await page.getByTestId('process-driver-open-user-task').click();
    await assertText(page.getByTestId('user-task-title'), 'Assess risk');
    // The form of this project, drawn by the host of the module kit into the areas of the dev shell.
    await assertText(page.getByTestId('assess-risk-loan-request'), 'LR-4711');
    await assertText(page.getByTestId('assess-risk-amount'), '25,000');
    await page.getByTestId('assess-risk-complete').click();
    await page.getByTestId('assess-risk-done').waitFor();
    assert.ok(calls.includes('POST /wm/loan-approval/api/LR-4711/assess-risk/task-1'), calls.join('\n'));
    await assertText(page.getByTestId('notifications'), 'success: Completed');
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('a state where the workflow ended links to the case and says that no user task is open', async () => {
    const { page, pageErrors, calls } = await openDevShell();
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="process-driver-state"] option').length > 0);
    await page.getByTestId('process-driver-state').selectOption(ENDED);
    await page.getByTestId('process-driver-advance').click();

    await assertText(page.getByTestId('process-driver-loan-request'), 'LR-4711');
    await assertText(page.getByTestId('process-driver-no-user-task'), 'The workflow ended in this state, so no user task is open.');
    assert.equal(await page.getByTestId('process-driver-open-workflow').getAttribute('href'), '#/workflow/wf-1');
    assert.equal(await page.getByTestId('process-driver-open-user-task').count(), 0);
    assert.equal(await page.getByTestId('process-driver-not-in-cockpit').count(), 0);
    assert.ok(!calls.some((call) => call.includes('/usertask/')), calls.join('\n'));
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('names the switch of the driver when the application has no driver', async () => {
    const { page, pageErrors } = await openDevShell({ driver: 404 });
    await assertText(page.getByTestId('process-driver-error'), "The process driver is not switched on (404). Start the application with the Maven profile 'test-driver' and the property 'process-driver.enabled=true'. The README of the backend shows the command.");
    assert.equal(await page.getByTestId('process-driver-advance').isDisabled(), true);
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('names the switch of the driver when the application does not answer at all', async () => {
    const { page, pageErrors } = await openDevShell({ driver: 502 });
    await assertText(page.getByTestId('process-driver-error'), "The application does not answer. Start the application with the Maven profile 'test-driver' and the property 'process-driver.enabled=true'.");
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('shows the reason when the case did not reach the state', async () => {
    const reason = "Loan approval 'LR-4711' did not reach ASSESS_RISK_USER_TASK: a small amount never needs a risk assessment";
    const { page, pageErrors } = await openDevShell({ advance: { status: 500, body: reason } });
    await page.getByTestId('process-driver-advance').click();
    await assertText(page.getByTestId('process-driver-error'), `The process driver answered 500. ${reason}`);
    assert.equal(await page.getByTestId('process-driver-result').count(), 0);
    assert.deepEqual(pageErrors, []);
    await page.close();
  });
});

describe('the pages of a case and of a user task', () => {
  it('the case lists its user tasks with a link to each', async () => {
    const { page, pageErrors } = await openDevShell({}, '#/workflow/wf-1');
    await assertText(page.getByTestId('workflow-title'), 'Loan approval');
    await assertText(page.getByTestId('workflow-business-id'), 'LR-4711');
    assert.equal(await page.getByTestId('workflow-user-task').getAttribute('href'), '#/user-task/task-1');
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('a user task the cockpit does not know shows a message', async () => {
    const { page, pageErrors } = await openDevShell({}, '#/user-task/unknown');
    await assertText(page.getByTestId('user-task-error'), 'The simulated cockpit does not know the user task unknown.');
    assert.deepEqual(pageErrors, []);
    await page.close();
  });
});

describe('the names of the web components', () => {
  it('a name without the prefix of the module is not drawn, and the dev shell says why', async () => {
    const { entries } = JSON.parse(readFileSync(join(microFrontends, 'bc-manifest.json'), 'utf8'));
    const page = await browser.newPage();
    const pageErrors = [];
    const consoleErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    page.on('console', (message) => message.type() === 'error' && consoleErrors.push(message.text()));
    const { calls } = await playBackend(page);
    // A data entry which names its form by hand, without the prefix of its module.
    await page.route(`**/wm/loan-approval/${entries.UserTaskForm}`, (route) => route.fulfill({
      contentType: 'text/javascript',
      body: "export const userTaskForm = () => ({ entry: 'AssessRiskForm', element: { body: 'assess-risk-body', actions: 'assess-risk-actions' } });",
    }));
    await page.goto(`${server.url}/#/user-task/task-1`);

    const note = "The module 'loan-approval' names the web component 'assess-risk-body', but its names have to start with 'loan-approval-'.";
    await assertText(page.getByTestId('user-task-error'), note);
    assert.ok(consoleErrors.includes(note), consoleErrors.join('\n'));
    // Nothing of the form was loaded, and nothing was drawn.
    const loaded = await page.evaluate(() => performance.getEntriesByType('resource').map(({ name }) => new URL(name).pathname));
    assert.ok(!loaded.includes(`/wm/loan-approval/${entries.AssessRiskForm}`), loaded.join('\n'));
    assert.equal(await page.locator('[data-testid="user-task-form"] [data-bc-area] > *').count(), 0);
    assert.ok(!calls.some((call) => call.includes('/assess-risk/')), calls.join('\n'));
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('the names of this build are drawn', async () => {
    const { BUILD_ID } = await import(pathToFileURL(join(projectDir, 'src', 'bc-build.js')).href);
    const { page, pageErrors } = await openDevShell({}, '#/user-task/task-1');
    await assertText(page.getByTestId('assess-risk-loan-request'), 'LR-4711');
    const drawn = await page.evaluate(() => [...document.querySelectorAll('[data-testid="user-task-form"] [data-bc-area] > *')].map((element) => element.localName));
    assert.deepEqual(drawn, [
      `loan-approval-${BUILD_ID}-loan-approval--assess-risk--body`,
      `loan-approval-${BUILD_ID}-loan-approval--assess-risk--actions`,
    ]);
    assert.equal(await page.getByTestId('user-task-error').count(), 0);
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('the check refuses what a cockpit refuses, and accepts a name without a build id', async () => {
    const bundle = join(projectDir, 'build', 'element-names.test.js');
    await build({ entryPoints: [join(projectDir, 'dev-shell', 'src', 'element-names.ts')], bundle: true, format: 'esm', outfile: bundle, logLevel: 'warning' });
    const { refusedElementName } = await import(pathToFileURL(bundle).href);
    const modules = ['taxi', 'taxi-ride', 'loan-approval'];

    assert.equal(refusedElementName('taxi', 'taxi-mvaro4w0-confirm', modules), undefined);
    // The check does not ask for a build id.
    assert.equal(refusedElementName('taxi-ride', 'taxi-ride-taxi-ride--confirm--form', modules), undefined);
    // The prefix comes from the module id by the rules of the kit.
    assert.equal(refusedElementName('TaxiRide', 'taxi-ride-mvaro4w0-confirm', modules), undefined);
    assert.match(refusedElementName('taxi', 'taxi-ride-confirm', modules), /starts with 'taxi-ride-', the prefix of another module/);
    assert.match(refusedElementName('taxi', 'taxiride-confirm', modules), /have to start with 'taxi-'/);
    assert.match(refusedElementName('taxi', 'taxi', modules), /not a valid name of a custom element/);
    assert.match(refusedElementName('taxi', 'Taxi-confirm', modules), /not a valid name of a custom element/);
    assert.match(refusedElementName('font', 'font-face', ['font']), /not a valid name of a custom element/);
  });
});

/** Waits until a locator shows a text, at most fifteen seconds: the dev shell waits for the cockpit. */
async function assertText(locator, expected) {
  try {
    await locator.filter({ hasText: expected }).waitFor({ timeout: 15000 });
  } catch {
    assert.equal(await locator.textContent(), expected);
  }
}
