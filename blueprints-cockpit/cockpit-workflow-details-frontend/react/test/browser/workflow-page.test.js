// The browser tests: the host of the module kit loads the built micro-frontends in Chromium, the
// way a cockpit loads them. Once as a cockpit with the areas body, information and actions, and
// once as a cockpit with the minimum only, the area 'all'. The module API is answered in the
// browser, at the level of the network, and the host answers getUserTasks the way a cockpit
// does. So the page runs unchanged.
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

/** A case as the cockpit hands it to the page: the id of the cockpit, and the business id. */
const loanApproval = { id: 'wf-1', businessId: 'LR-4711', bpmnProcessId: 'loan_approval', title: { en: 'Loan approval' } };
const reviewLoanRequest = {
  id: 'task-1',
  workflowId: 'wf-1',
  bpmnProcessId: 'loan_approval',
  taskDefinition: 'reviewLoanRequest',
  title: { en: 'Review loan request', de: 'Kreditantrag prüfen' },
};
const runningCase = { loanRequestId: 'LR-4711', applicant: 'Jane Doe', amount: 25000, status: 'REQUESTED' };

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
 * Answers the module API in the browser. GET returns `cases[0]` until the loan request was
 * withdrawn, and `cases[1]` after that, or `status` if one is given. POST answers
 * `withdrawStatus`, 204 by default, and keeps the id it got.
 */
async function mockApi(page, { cases = [runningCase, { ...runningCase, status: 'WITHDRAWN' }], status, withdrawStatus = 204 } = {}) {
  const withdrawn = [];
  await page.route('**/wm/loan-approval/api/cases/**', async (route) => {
    const request = route.request();
    const [, loanRequestId] = new URL(request.url()).pathname.match(/\/cases\/([^/]+)/);
    if (status) return route.fulfill({ status });
    if (request.method() === 'POST') {
      if (withdrawStatus === 204) withdrawn.push(loanRequestId);
      return route.fulfill({ status: withdrawStatus });
    }
    return route.fulfill({ json: withdrawn.length === 0 ? cases[0] : cases[1] });
  });
  return withdrawn;
}

async function openPage(query, api) {
  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const withdrawn = await mockApi(page, api);
  await page.goto(`${server.url}/${query}`);
  await page.waitForFunction(() => window.bcTest !== undefined);
  return { page, pageErrors, withdrawn };
}

/** Puts the user tasks the cockpit answers getUserTasks with. */
const cockpitHasUserTasks = (page, userTasks) => page.evaluate((tasks) => { window.bcTest.userTasks = tasks; }, userTasks);

const show = (page, workflow = loanApproval) => page.evaluate((data) => window.bcTest.show(data), workflow);

/** The web components the host drew, with the mode of their shadow roots. */
const drawnElements = (page) => page.evaluate(() => [...document.querySelectorAll('#workflow-page [data-bc-area] > *, #workflow-page > :not([data-bc-area])')]
  .map((element) => [element.localName, element.shadowRoot?.mode]));

/** The paths below /wm/loan-approval/ the page has loaded so far. */
const loadedFiles = (page) => page.evaluate(() => performance.getEntriesByType('resource')
  .map(({ name }) => new URL(name).pathname)
  .filter((path) => path.startsWith('/wm/loan-approval/') && path.endsWith('.js')));

const manifest = () => JSON.parse(readFileSync(join(microFrontends, 'bc-manifest.json'), 'utf8'));

describe('the data entries', () => {
  it('WorkflowPage names the visual entry and the web components of a loan approval', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    const pages = await page.evaluate(async (workflows) => Promise.all(
      workflows.map((workflow) => window.bcTest.host.ask('loan-approval', 'WorkflowPage', [workflow])),
    ), [loanApproval, { ...loanApproval, bpmnProcessId: 'other' }]);
    assert.deepEqual(pages, [
      {
        entry: 'LoanApprovalPage',
        element: {
          all: 'loan-approval-case',
          body: 'loan-approval-case-body',
          information: 'loan-approval-case-information',
          actions: 'loan-approval-case-actions',
        },
      },
      undefined,
    ]);
    await page.close();
  });

  it('WorkflowList shows the applicant and the status from the details of a case', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    const columns = await page.evaluate(async (workflows) => Promise.all(
      workflows.map((workflow) => window.bcTest.host.ask('loan-approval', 'WorkflowList', [workflow])),
    ), [loanApproval, { ...loanApproval, bpmnProcessId: 'other' }]);
    assert.deepEqual(columns[0].map(({ path, title }) => [path, title]), [
      ['details.applicant', { en: 'Applicant', de: 'Antragsteller' }],
      ['details.status', { en: 'Status', de: 'Status' }],
    ]);
    assert.equal(columns[1], undefined);
    await page.close();
  });

  it('asking the data entries loads neither the page nor React', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    await page.evaluate(async (workflow) => {
      await window.bcTest.host.ask('loan-approval', 'WorkflowPage', [workflow]);
      await window.bcTest.host.ask('loan-approval', 'WorkflowList', [workflow]);
    }, loanApproval);
    const loaded = await loadedFiles(page);
    const { entries } = manifest();
    assert.ok(loaded.includes(`/wm/loan-approval/${entries.WorkflowPage}`), loaded.join(', '));
    assert.ok(loaded.includes(`/wm/loan-approval/${entries.WorkflowList}`), loaded.join(', '));
    assert.ok(!loaded.some((path) => path.includes('LoanApprovalPage-')), loaded.join(', '));
    for (const path of loaded) {
      const source = readFileSync(join(microFrontends, path.slice('/wm/loan-approval/'.length)), 'utf8');
      assert.deepEqual(findFrameworkCode(source), [], path);
    }
    await page.close();
  });
});

for (const cockpit of COCKPITS) {
  describe(`the case page in ${cockpit.name}`, () => {
    it('shows the data of the loan request and the open user tasks the cockpit lists', async () => {
      const { page, pageErrors } = await openPage(cockpit.query);
      await cockpitHasUserTasks(page, [reviewLoanRequest]);
      assert.deepEqual(await show(page), cockpit.areas);

      const expected = cockpit.areas.map((area) => [area === 'all' ? 'loan-approval-case' : `loan-approval-case-${area}`, 'open']);
      assert.deepEqual(await drawnElements(page), expected);

      await assertText(page.getByTestId('case-loan-request'), 'LR-4711');
      await assertText(page.getByTestId('case-applicant'), 'Jane Doe');
      await assertText(page.getByTestId('case-amount'), '25,000');
      await assertText(page.getByTestId('case-status'), 'Requested');
      await assertText(page.getByTestId('case-user-task'), 'Review loan request');

      // The page asked the cockpit once, for the open user tasks of this case.
      assert.deepEqual(await page.evaluate(() => window.bcTest.userTaskRequests), [
        { workflowId: 'wf-1', activeOnly: true, limitListAccordingToCurrentUsersPermissions: false },
      ]);
      const withdraw = page.getByTestId('case-withdraw');
      await assertEnabled(withdraw);
      // The styles of the page do not reach the button in the shadow DOM.
      assert.notEqual(await withdraw.evaluate((button) => getComputedStyle(button).backgroundColor), 'rgb(0, 128, 0)');

      const loaded = await loadedFiles(page);
      assert.ok(loaded.some((path) => path.includes('/LoanApprovalPage-')), loaded.join(', '));
      assert.deepEqual(await page.evaluate(() => window.bcTest.errors), []);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('"Withdraw loan request" asks the module to withdraw it, and the page shows it withdrawn without its user task', async () => {
      const { page, pageErrors, withdrawn } = await openPage(cockpit.query);
      await cockpitHasUserTasks(page, [reviewLoanRequest]);
      await show(page);
      await assertText(page.getByTestId('case-user-task'), 'Review loan request');

      // Once the event subprocess ended the user task, the cockpit lists none.
      await cockpitHasUserTasks(page, []);
      await page.getByTestId('case-withdraw').click({ timeout: 5000 });
      await page.getByTestId('case-withdrawn').waitFor();

      assert.deepEqual(withdrawn, ['LR-4711']);
      assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), [{ level: 'success', text: 'The loan request is withdrawn.', sticky: false }]);
      // The other areas got the message and loaded their data again.
      await assertText(page.getByTestId('case-status'), 'Withdrawn');
      await page.getByTestId('case-no-user-tasks').waitFor({ timeout: 5000 });
      assert.equal(await page.getByTestId('case-user-task').count(), 0);
      assert.equal(await page.getByTestId('case-withdraw').isDisabled(), true);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('asks for the user tasks again when the cockpit sets the case again', async () => {
      const { page, pageErrors } = await openPage(cockpit.query);
      await cockpitHasUserTasks(page, [reviewLoanRequest]);
      await show(page);
      await assertText(page.getByTestId('case-user-task'), 'Review loan request');

      // The update stream of the cockpit says that the case changed.
      await cockpitHasUserTasks(page, [reviewLoanRequest, { ...reviewLoanRequest, id: 'task-2', title: { en: 'Call the applicant' } }]);
      await page.evaluate((workflow) => window.bcTest.part.update({ workflow: { ...workflow, updatedAt: '2026-11-13T10:00:00Z' } }), loanApproval);
      await page.getByTestId('case-user-task').nth(1).waitFor({ timeout: 5000 });
      await assertText(page.getByTestId('case-user-task').nth(1), 'Call the applicant');
      assert.equal(await page.evaluate(() => window.bcTest.userTaskRequests.length), 2);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('an approved loan request has no enabled "Withdraw loan request"', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { cases: [{ ...runningCase, status: 'APPROVED' }] });
      await show(page, { ...loanApproval, endedAt: '2026-11-13T09:00:00Z' });
      await assertText(page.getByTestId('case-status'), 'Approved');
      await page.getByTestId('case-no-user-tasks').waitFor({ timeout: 5000 });
      assert.equal(await page.getByTestId('case-withdraw').isDisabled(), true);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('long content wraps and does not widen the page', async () => {
      // Words without a place to break them, the hardest case for a layout.
      const longName = 'Maximilianekonstanze'.repeat(20);
      const { page, pageErrors } = await openPage(cockpit.query, { cases: [{ ...runningCase, applicant: longName }] });
      await cockpitHasUserTasks(page, Array.from({ length: 30 }, (_, index) => ({ ...reviewLoanRequest, id: `task-${index}`, title: { en: `Review${'verylong'.repeat(20)}${index}` } })));
      await show(page);
      await assertText(page.getByTestId('case-applicant'), longName);
      await page.getByTestId('case-user-task').nth(29).waitFor({ timeout: 5000 });

      // An element whose content is wider than the element itself overflows, even where the
      // element keeps its own width. scrollWidth tells it.
      const overflowing = await page.evaluate(() => {
        const container = document.getElementById('workflow-page');
        return [container, ...container.querySelectorAll('*')]
          .flatMap((element) => [element, ...(element.shadowRoot ? element.shadowRoot.querySelectorAll('*') : [])])
          .filter((element) => element.clientWidth > 0 && element.scrollWidth > element.clientWidth + 1)
          .map((element) => element.localName);
      });
      assert.deepEqual(overflowing, []);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a case the module does not know shows a message instead of its data', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { status: 404 });
      await show(page);
      await assertText(page.getByTestId('case-error'), 'The workflow module does not know this case.');
      assert.equal(await page.getByTestId('case-applicant').count(), 0);
      assert.equal(await page.getByTestId('case-withdraw').isDisabled(), true);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a withdrawal the module refuses shows why', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { withdrawStatus: 409 });
      await show(page);
      await page.getByTestId('case-withdraw').click({ timeout: 5000 });
      await assertText(page.getByTestId('case-withdraw-error'), 'This case has ended already.');
      assert.equal(await page.getByTestId('case-withdrawn').count(), 0);
      assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), [{ level: 'error', text: 'This case has ended already.', sticky: false }]);
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

/** Waits until a button is enabled, at most five seconds. */
async function assertEnabled(locator) {
  for (let tries = 0; tries < 50 && await locator.isDisabled(); tries += 1) {
    await new Promise((done) => setTimeout(done, 100));
  }
  assert.equal(await locator.isDisabled(), false);
}
