// The browser tests: the host of the module kit loads the built micro-frontends in Chromium, the
// way a cockpit loads them. Once as a cockpit with the areas body, information and actions, and
// once as a cockpit with the minimum only, the area 'all'. The module API is answered in the
// browser, at the level of the network, with the BPMN file next to this test and a history the
// test chooses. So the page and bpmn-js run unchanged.
//
// Run `npm run build` before; the tests read dist/micro-frontends and build/meta.json.

import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { after, before, describe, it } from 'node:test';
import { build } from 'esbuild';
import { chromium } from 'playwright';
import { findFrameworkCode } from '@vanillabp/bc-module-kit/check';
import { startServer } from './server.js';

const projectDir = resolve(import.meta.dirname, '..', '..');
const microFrontends = join(projectDir, 'dist', 'micro-frontends');
const hostBundle = join(projectDir, 'build', 'test-host.js');

/**
 * The web component of the case page, as the built data entry has to name it. The name carries
 * the id of the build, which the last `npm run build` wrote into src/bc-build.js.
 */
const { BUILD_ID } = await import(pathToFileURL(join(projectDir, 'src', 'bc-build.js')).href);
const PROCESS_ELEMENT = `loan-approval-${BUILD_ID}-loan-approval--process`;

/** A case as the cockpit hands it to the page: the id of the cockpit, and the business id. */
const loanApproval = { id: 'wf-1', businessId: 'LR-4711', bpmnProcessId: 'loan_approval', title: { en: 'Loan approval' } };

/**
 * The model of the backend, a copy of loan_approval.bpmn of bpmn-history-and-diagram. Its ids:
 * StartEvent_LoanRequested, ServiceTask_RetrieveCreditRating, SendTask_AwaitPartnerApproval,
 * ServiceTask_DecideOnLoan and EndEvent_LoanDecided.
 */
const BPMN_XML = readFileSync(join(import.meta.dirname, 'loan_approval.bpmn'), 'utf8');

const passed = (elementId, minute) => ({
  elementId,
  elementType: 'TASK',
  startTime: `2026-11-25T08:${minute}:00Z`,
  endTime: `2026-11-25T08:${minute}:30Z`,
  isCanceled: false,
});
const current = (elementId, minute) => ({ ...passed(elementId, minute), endTime: null });

/** A case which waits for the approval of the partner. */
const WAITING = {
  processDefinitionId: 'camunda7:loan_approval:1',
  startTime: '2026-11-25T08:00:00Z',
  endTime: null,
  elementsHistory: [
    passed('StartEvent_LoanRequested', '00'),
    passed('ServiceTask_RetrieveCreditRating', '01'),
    current('SendTask_AwaitPartnerApproval', '02'),
    // An id the diagram does not have. The page leaves it out.
    passed('Activity_NotInTheDiagram', '01'),
  ],
};
/** The same case after the approval, deciding on the loan. */
const DECIDING = {
  ...WAITING,
  elementsHistory: [
    passed('StartEvent_LoanRequested', '00'),
    passed('ServiceTask_RetrieveCreditRating', '01'),
    passed('SendTask_AwaitPartnerApproval', '02'),
    current('ServiceTask_DecideOnLoan', '03'),
  ],
};
/** The same case after it ended. */
const ENDED = {
  ...WAITING,
  endTime: '2026-11-25T08:05:00Z',
  elementsHistory: [...DECIDING.elementsHistory.slice(0, 3), passed('ServiceTask_DecideOnLoan', '03'), passed('EndEvent_LoanDecided', '04')],
};

const ALL_ELEMENTS = ['StartEvent_LoanRequested', 'ServiceTask_RetrieveCreditRating', 'SendTask_AwaitPartnerApproval', 'ServiceTask_DecideOnLoan', 'EndEvent_LoanDecided'];

/** The two kinds of cockpit: with the areas of the default convention, and with 'all' only. */
const COCKPITS = [
  { name: 'a cockpit with areas', query: '?areas=body,information,actions' },
  { name: 'a cockpit with the area all only', query: '' },
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
 * Answers the module API in the browser: the BPMN XML for /diagram and `api.history` for
 * /history. `status` answers both with that status instead. The test may change `api.history`
 * while the page is open. The ids of the loan requests asked for are kept in `api.asked`.
 */
async function mockApi(page, api) {
  api.asked = [];
  await page.route('**/wm/loan-approval/api/**', (route) => {
    const [, loanRequestId, endpoint] = new URL(route.request().url()).pathname.match(/\/api\/([^/]+)\/([^/]+)$/);
    api.asked.push(`${endpoint} ${loanRequestId}`);
    if (api.status) return route.fulfill({ status: api.status });
    if (endpoint === 'diagram') return route.fulfill({ contentType: 'application/xml', body: BPMN_XML });
    if (endpoint === 'history') return route.fulfill({ json: api.history });
    return route.fulfill({ status: 404 });
  });
}

async function openPage(query, api = { history: WAITING }) {
  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await mockApi(page, api);
  await page.goto(`${server.url}/${query}`);
  await page.waitForFunction(() => window.bcTest !== undefined);
  return { page, pageErrors, api };
}

const show = (page, workflow = loanApproval) => page.evaluate((data) => window.bcTest.show(data), workflow);

/** The web components the host drew, with the mode of their shadow roots. */
const drawnElements = (page) => page.evaluate(() => [...document.querySelectorAll('#workflow-page [data-bc-area] > *, #workflow-page > :not([data-bc-area])')]
  .map((element) => [element.localName, element.shadowRoot?.mode]));

/** The paths below /wm/loan-approval/ the page has loaded so far. */
const loadedFiles = (page) => page.evaluate(() => performance.getEntriesByType('resource')
  .map(({ name }) => new URL(name).pathname)
  .filter((path) => path.startsWith('/wm/loan-approval/') && path.endsWith('.js')));

const manifest = () => JSON.parse(readFileSync(join(microFrontends, 'bc-manifest.json'), 'utf8'));

/** The path of the one built file with bpmn-js in it, from the list of outputs esbuild wrote. */
function bpmnJsChunk() {
  const { outputs } = JSON.parse(readFileSync(join(projectDir, 'build', 'meta.json'), 'utf8'));
  const withBpmnJs = Object.entries(outputs)
    .filter(([, { inputs }]) => Object.keys(inputs).some((input) => input.includes('node_modules/bpmn-js/')));
  assert.equal(withBpmnJs.length, 1, `bpmn-js is in one file, found ${withBpmnJs.map(([output]) => output).join(', ')}`);
  return `/wm/loan-approval/chunks/${basename(withBpmnJs[0][0])}`;
}

/** The marker classes of each element of the diagram, by its id. */
const markersOf = (page) => page.evaluate((ids) => {
  const root = document.querySelector('#workflow-page > :not([data-bc-area])').shadowRoot;
  return Object.fromEntries(ids.map((id) => {
    const element = root.querySelector(`.djs-element[data-element-id="${id}"]`);
    return [id, element ? [...element.classList].filter((name) => name.startsWith('bc-')) : 'missing'];
  }));
}, ALL_ELEMENTS);

describe('the data entry', () => {
  it('WorkflowPage names the visual entry and the web component of a loan approval', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    const pages = await page.evaluate(async (workflows) => Promise.all(
      workflows.map((workflow) => window.bcTest.host.ask('loan-approval', 'WorkflowPage', [workflow])),
    ), [loanApproval, { ...loanApproval, bpmnProcessId: 'other' }]);
    assert.deepEqual(pages, [{ entry: 'LoanApprovalProcessPage', element: PROCESS_ELEMENT }, undefined]);
    await page.close();
  });

  it('asking the data entry loads neither the page, nor Vue, nor bpmn-js', async () => {
    const { page } = await openPage(COCKPITS[0].query);
    await page.evaluate((workflow) => window.bcTest.host.ask('loan-approval', 'WorkflowPage', [workflow]), loanApproval);
    const loaded = await loadedFiles(page);
    assert.ok(loaded.includes(`/wm/loan-approval/${manifest().entries.WorkflowPage}`), loaded.join(', '));
    assert.ok(!loaded.some((path) => path.includes('LoanApprovalProcessPage-')), loaded.join(', '));
    assert.ok(!loaded.includes(bpmnJsChunk()), loaded.join(', '));
    for (const path of loaded) {
      const source = readFileSync(join(microFrontends, path.slice('/wm/loan-approval/'.length)), 'utf8');
      assert.deepEqual(findFrameworkCode(source), [], path);
    }
    await page.close();
  });
});

for (const cockpit of COCKPITS) {
  describe(`the case page in ${cockpit.name}`, () => {
    it('draws the process with bpmn-js, marks the passed steps and the current one, and keeps the bpmn.io logo', async () => {
      const { page, pageErrors, api } = await openPage(cockpit.query);
      // The page offers the area 'all' only, so a cockpit with areas shows it as well.
      assert.deepEqual(await show(page), ['all']);
      assert.deepEqual(await drawnElements(page), [[PROCESS_ELEMENT, 'open']]);

      await page.locator('.djs-element[data-element-id="SendTask_AwaitPartnerApproval"]').waitFor({ timeout: 5000 });
      assert.deepEqual(await markersOf(page), {
        StartEvent_LoanRequested: ['bc-passed'],
        ServiceTask_RetrieveCreditRating: ['bc-passed'],
        SendTask_AwaitPartnerApproval: ['bc-current'],
        ServiceTask_DecideOnLoan: [],
        EndEvent_LoanDecided: [],
      });
      assert.deepEqual(api.asked.toSorted(), ['diagram LR-4711', 'history LR-4711']);

      // The styles of bpmn-js are in the shadow root: the marker colours the outline of the task.
      const outline = page.locator('.djs-element[data-element-id="SendTask_AwaitPartnerApproval"] .djs-visual > :first-child');
      assert.equal(await outline.evaluate((shape) => getComputedStyle(shape).stroke), 'rgb(178, 106, 0)');
      // The license of bpmn.io asks to keep its logo visible.
      assert.equal(await page.locator('.bjs-powered-by').isVisible(), true);
      await page.getByText('Current step').waitFor({ timeout: 5000 });
      // The styles of the module stay in its shadow root: the page keeps its one <style> element,
      // and the style of the page does not reach the heading in the shadow root.
      assert.equal(await page.evaluate(() => document.querySelectorAll('style').length), 1);
      assert.notEqual(await page.getByRole('heading', { name: 'Process' }).evaluate((heading) => getComputedStyle(heading).color), 'rgb(0, 128, 0)');

      // Only the page loads bpmn-js, in a chunk of its own.
      const loaded = await loadedFiles(page);
      assert.ok(loaded.some((path) => path.includes('/LoanApprovalProcessPage-')), loaded.join(', '));
      assert.ok(loaded.includes(bpmnJsChunk()), loaded.join(', '));
      assert.deepEqual(await page.evaluate(() => window.bcTest.errors), []);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('moves the marker of the current step when the cockpit sets the case again', async () => {
      const { page, pageErrors, api } = await openPage(cockpit.query);
      await show(page);
      await page.locator('.djs-element.bc-current[data-element-id="SendTask_AwaitPartnerApproval"]').waitFor({ timeout: 5000 });

      // The update stream of the cockpit says that the case changed.
      api.history = DECIDING;
      await page.evaluate((workflow) => window.bcTest.part.update({ workflow: { ...workflow, updatedAt: '2026-11-25T08:03:00Z' } }), loanApproval);
      await page.locator('.djs-element.bc-current[data-element-id="ServiceTask_DecideOnLoan"]').waitFor({ timeout: 5000 });
      assert.deepEqual(await markersOf(page), {
        StartEvent_LoanRequested: ['bc-passed'],
        ServiceTask_RetrieveCreditRating: ['bc-passed'],
        SendTask_AwaitPartnerApproval: ['bc-passed'],
        ServiceTask_DecideOnLoan: ['bc-current'],
        EndEvent_LoanDecided: [],
      });
      // The diagram of before is gone, so the page shows one diagram.
      assert.equal(await page.locator('.bjs-container').count(), 1);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a case which ended shows every step it went through as passed, and none as current', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { history: ENDED });
      await show(page, { ...loanApproval, endedAt: '2026-11-25T08:05:00Z' });
      await page.locator('.djs-element.bc-passed[data-element-id="EndEvent_LoanDecided"]').waitFor({ timeout: 5000 });
      assert.deepEqual(await markersOf(page), Object.fromEntries(ALL_ELEMENTS.map((id) => [id, ['bc-passed']])));
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a case the module does not know shows a message instead of the process', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { status: 404 });
      await show(page);
      await assertText(page.getByTestId('process-error'), 'The workflow module does not know this case.');
      assert.equal(await page.getByTestId('process-diagram').isVisible(), false);
      assert.equal(await page.locator('.djs-element').count(), 0);
      assert.deepEqual(pageErrors, []);
      await page.close();
    });

    it('a module which fails shows that it did not answer', async () => {
      const { page, pageErrors } = await openPage(cockpit.query, { status: 500 });
      await show(page);
      await assertText(page.getByTestId('process-error'), 'The workflow module did not answer. Please try again.');
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
