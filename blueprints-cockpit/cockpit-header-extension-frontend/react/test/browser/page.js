// The script of the test page. It puts the host of the module kit into the page, the way a
// cockpit loads its workflow modules, and hands the tests what they need.
//
// ?header gives the host the data entry Header, like a cockpit with a header. Without it, the host
// is a cockpit with the minimum only, which does not know Header.
//
// ?modules=loan-approval,module-without-header is the list of modules the user may see. A cockpit
// gets it from GET /gui/api/v1/workflow-module, which leaves out every module whose groups the user
// is not in. By default the list holds loan-approval only.

import { createModuleHost } from '@vanillabp/bc-module-host';

const notifications = [];
const errors = [];
const query = new URLSearchParams(location.search);
const visibleModules = (query.get('modules') ?? 'loan-approval').split(',').filter(Boolean);
const currentUser = { id: 'jane' };
/** What a cockpit hands every web component of a module. */
const properties = { language: query.get('language') ?? 'en', currentUser };

const host = createModuleHost({
  loadModules: () => visibleModules.map((id) => ({ id, uri: `/wm/${id}` })),
  entries: query.has('header') ? { Header: 'header' } : undefined,
  properties,
  actions: {
    'cockpit.showNotification': (args, { moduleId }) => { notifications.push({ moduleId, ...args }); },
  },
  logger: { warn: (message) => errors.push(message), error: (message) => errors.push(message) },
});

window.bcTest = {
  host,
  notifications,
  errors,
  /**
   * Draws the header the way a cockpit with a header does: it asks the entry Header of every
   * module the user may see, loads the visual entries the answers name, and draws their web
   * components side by side. A module without the entry answers undefined and is skipped. A
   * cockpit without a header does not know the entry, and the host answers undefined for every
   * module.
   *
   * @returns {Promise<Array<{ moduleId: string, element: string }>>} what was drawn
   */
  async showHeader() {
    await host.registry.load();
    const drawn = [];
    for (const { id: moduleId } of host.registry.list()) {
      const buttons = await host.ask(moduleId, 'Header', [currentUser]);
      for (const { entry, element } of buttons ?? []) {
        await host.load(moduleId, entry, [element]);
        host.draw({ moduleId, entry, element, container: document.getElementById('header'), properties: { ...properties, workflowModule: host.registry.get(moduleId) } });
        drawn.push({ moduleId, element });
      }
    }
    return drawn;
  },
};
