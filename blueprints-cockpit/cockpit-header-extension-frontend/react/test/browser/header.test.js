// The browser tests: the host of the module kit loads the built micro-frontends in Chromium, the
// way a cockpit loads them. Once as a cockpit with a header, which asks the entry Header of every
// module the user may see, and once as a cockpit with the minimum only, which does not know the
// entry. The module API is answered in the browser, at the level of the network. So the button and
// the form run unchanged.
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

/** The cockpit asks the entry Header with the user who is logged in. */
const jane = { id: 'jane' };

/** The web component of the button, as the data entry names it. */
const BUTTON = 'loan-approval-request-loan-button';

/** The web component of the start form, which the cockpit shows in a dialog. */
const FORM = 'loan-approval-request-loan-form';

let server;
let browser;

before(async () => {
  if (!existsSync(join(microFrontends, 'bc-manifest.json'))) {
    throw new Error('dist/micro-frontends/bc-manifest.json is missing. Run `npm run build` before `npm test`.');
  }
  await build({ entryPoints: [join(import.meta.dirname, 'page.js')], bundle: true, format: 'esm', outfile: hostBundle, logLevel: 'warning' });
  server = await startServer({
    files: { '/': join(import.meta.dirname, 'index.html'), '/host.js': hostBundle },
    folders: {
      '/wm/loan-approval/': microFrontends,
      '/wm/module-without-header/': join(import.meta.dirname, 'module-without-header'),
    },
  });
  browser = await chromium.launch(process.env.BC_CHROMIUM_PATH ? { executablePath: process.env.BC_CHROMIUM_PATH } : {});
});

after(async () => {
  await browser?.close();
  await server?.close();
});

/**
 * Answers the module API in the browser. POST /api/loan-requests answers `status`, 201 with a new
 * id by default, and keeps every body it got.
 */
async function mockApi(page, { status = 201 } = {}) {
  const received = [];
  await page.route('**/wm/loan-approval/api/loan-requests', async (route) => {
    const request = route.request();
    if (request.method() !== 'POST') return route.fulfill({ status: 405 });
    received.push(request.postDataJSON());
    if (status !== 201) return route.fulfill({ status });
    return route.fulfill({ status: 201, json: { loanRequestId: `LR-${received.length}` } });
  });
  return received;
}

async function openPage(query, api) {
  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const received = await mockApi(page, api);
  await page.goto(`${server.url}/${query}`);
  await page.waitForFunction(() => window.bcTest !== undefined);
  return { page, pageErrors, received };
}

const showHeader = (page) => page.evaluate(() => window.bcTest.showHeader());

/** The paths below /wm/ the page has loaded so far. */
const loadedFiles = (page) => page.evaluate(() => performance.getEntriesByType('resource')
  .map(({ name }) => new URL(name).pathname)
  .filter((path) => path.startsWith('/wm/')));

const manifest = () => JSON.parse(readFileSync(join(microFrontends, 'bc-manifest.json'), 'utf8'));

/** Opens the start form with the button in the header, and waits for the dialog. */
async function openStartForm(page) {
  await page.getByTestId('request-loan').click();
  await page.getByTestId('start-form').waitFor();
}

async function fillIn(page, { applicant, amount }) {
  await page.getByTestId('start-form-applicant').fill(applicant);
  await page.getByTestId('start-form-amount').fill(amount);
}

const dialogs = (page) => page.locator('dialog[data-bc-dialog]');

describe('the data entry Header', () => {
  it('names the button of the start form for the user who is logged in', async () => {
    const { page } = await openPage('?header');
    const answers = await page.evaluate(async (user) => [
      await window.bcTest.host.ask('loan-approval', 'Header', [user]),
      await window.bcTest.host.ask('loan-approval', 'Header', [undefined]),
    ], jane);
    assert.deepEqual(answers, [[{ entry: 'StartForm', element: BUTTON }], undefined]);
    await page.close();
  });

  it('asking it loads neither the start form nor React', async () => {
    const { page } = await openPage('?header');
    await page.evaluate((user) => window.bcTest.host.ask('loan-approval', 'Header', [user]), jane);
    const loaded = await loadedFiles(page);
    const { entries } = manifest();
    assert.ok(loaded.includes(`/wm/loan-approval/${entries.Header}`), loaded.join(', '));
    assert.ok(!loaded.some((path) => path.includes('StartForm-')), loaded.join(', '));
    // The data entry may share a chunk with the start form, for the names both use. No file it
    // loads may carry React.
    for (const path of loaded.filter((loadedPath) => loadedPath.endsWith('.js'))) {
      const source = readFileSync(join(microFrontends, path.slice('/wm/loan-approval/'.length)), 'utf8');
      assert.deepEqual(findFrameworkCode(source), [], path);
    }
    await page.close();
  });
});

describe('a cockpit with a header', () => {
  it('shows the button "Request a loan" in a shadow root of its own', async () => {
    const { page, pageErrors } = await openPage('?header');
    assert.deepEqual(await showHeader(page), [{ moduleId: 'loan-approval', element: BUTTON }]);
    const button = page.getByTestId('request-loan');
    assert.equal(await button.textContent(), 'Request a loan');
    assert.equal(await page.locator(`#header ${BUTTON}`).evaluate((element) => element.shadowRoot?.mode), 'open');
    // The page makes every button green. The button of the module keeps its own colour.
    assert.notEqual(await button.evaluate((element) => getComputedStyle(element).backgroundColor), 'rgb(0, 128, 0)');
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('opens the start form in a dialog, and OK starts a loan approval through the module', async () => {
    const { page, pageErrors, received } = await openPage('?header');
    await showHeader(page);
    await openStartForm(page);
    assert.equal(await dialogs(page).count(), 1);
    assert.equal(await dialogs(page).getAttribute('data-bc-dialog'), FORM);
    assert.equal(await page.getByTestId('request-loan').isDisabled(), true);

    await fillIn(page, { applicant: '  Jane Doe ', amount: '25000' });
    await page.getByTestId('start-form-ok').click();

    await dialogs(page).waitFor({ state: 'detached' });
    assert.deepEqual(received, [{ applicant: 'Jane Doe', amount: 25000 }]);
    await page.waitForFunction(() => window.bcTest.notifications.length > 0);
    assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), [
      { moduleId: 'loan-approval', level: 'success', text: 'The loan request is started. Its id is LR-1', sticky: false },
    ]);
    assert.equal(await page.getByTestId('request-loan').isEnabled(), true);
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('"Cancel" closes the dialog and sends nothing', async () => {
    const { page, received } = await openPage('?header');
    await showHeader(page);
    await openStartForm(page);
    await fillIn(page, { applicant: 'Jane Doe', amount: '25000' });
    await page.getByTestId('start-form-cancel').click();
    await dialogs(page).waitFor({ state: 'detached' });
    assert.deepEqual(received, []);
    assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), []);
    assert.equal(await page.getByTestId('request-loan').isEnabled(), true);
    await page.close();
  });

  it('checks the values before it sends them, and keeps the dialog open', async () => {
    const { page, received } = await openPage('?header');
    await showHeader(page);
    await openStartForm(page);
    for (const values of [{ applicant: ' ', amount: '25000' }, { applicant: 'Jane Doe', amount: '0' }, { applicant: 'Jane Doe', amount: '' }]) {
      await fillIn(page, values);
      await page.getByTestId('start-form-ok').click();
      assert.equal(await page.getByTestId('start-form-error').textContent(), 'Please enter the applicant and an amount above zero.');
    }
    assert.deepEqual(received, []);
    assert.equal(await dialogs(page).count(), 1);
    await page.close();
  });

  for (const [status, text] of [
    [400, 'The workflow module did not accept the loan request.'],
    [503, 'The workflow module did not answer. Please try again.'],
  ]) {
    it(`keeps the dialog open when the module answers ${status}`, async () => {
      const { page, received } = await openPage('?header', { status });
      await showHeader(page);
      await openStartForm(page);
      await fillIn(page, { applicant: 'Jane Doe', amount: '25000' });
      await page.getByTestId('start-form-ok').click();
      assert.equal(await page.getByTestId('start-form-error').textContent(), text);
      assert.equal(received.length, 1);
      assert.equal(await dialogs(page).count(), 1);
      assert.equal(await page.getByTestId('start-form-ok').isEnabled(), true);
      assert.deepEqual(await page.evaluate(() => window.bcTest.notifications), []);
      await page.close();
    });
  }

  it('speaks the language of the user', async () => {
    const { page } = await openPage('?header&language=de');
    await showHeader(page);
    assert.equal(await page.getByTestId('request-loan').textContent(), 'Kredit beantragen');
    await openStartForm(page);
    assert.equal(await page.getByTestId('start-form-cancel').textContent(), 'Abbrechen');
    await page.close();
  });
});

describe('who sees the button', () => {
  it('a user outside the group of the module gets no button, and nothing of the module is loaded', async () => {
    // The cockpit leaves loan-approval out of the list of modules for this user, because the
    // backend names the group LOAN_OFFICERS and the user is not in it.
    const { page, pageErrors } = await openPage('?header&modules=module-without-header');
    assert.deepEqual(await showHeader(page), []);
    assert.equal(await page.locator(BUTTON).count(), 0);
    assert.deepEqual((await loadedFiles(page)).filter((path) => path.startsWith('/wm/loan-approval/')), []);
    assert.deepEqual(pageErrors, []);
    await page.close();
  });

  it('a second module without the entry Header changes nothing: the header shows the one button', async () => {
    const { page, pageErrors } = await openPage('?header&modules=loan-approval,module-without-header');
    assert.deepEqual(await showHeader(page), [{ moduleId: 'loan-approval', element: BUTTON }]);
    assert.equal(await page.locator(`#header > *`).count(), 1);
    // The host read the manifest of the second module and asked nothing more.
    assert.ok((await loadedFiles(page)).includes('/wm/module-without-header/bc-manifest.json'));
    assert.deepEqual(await page.evaluate(() => window.bcTest.errors), []);
    assert.deepEqual(pageErrors, []);
    await page.close();
  });
});

describe('a cockpit without a header', () => {
  it('never asks the entry Header, loads nothing of the button, and shows no error', async () => {
    const { page, pageErrors } = await openPage('?modules=loan-approval');
    assert.deepEqual(await page.evaluate(() => window.bcTest.host.entries.includes('Header')), false);
    assert.deepEqual(await showHeader(page), []);
    assert.equal(await page.locator(BUTTON).count(), 0);
    const loaded = await loadedFiles(page);
    assert.ok(!loaded.some((path) => /\/(Header|StartForm)-/.test(path)), loaded.join(', '));
    assert.deepEqual(await page.evaluate(() => window.bcTest.errors), []);
    assert.deepEqual(pageErrors, []);
    // The rest of the module is untouched: the manifest still names both entries, for a cockpit
    // which knows them.
    assert.deepEqual(Object.keys(await page.evaluate(() => window.bcTest.host.readManifest('loan-approval').then((read) => read.entries))).sort(), ['Header', 'StartForm']);
    await page.close();
  });
});
