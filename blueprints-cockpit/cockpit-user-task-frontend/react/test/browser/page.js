// The script of the test page. It puts the host of the module kit into the page, the way a
// cockpit loads a workflow module, and hands the tests what they need.
//
// ?areas=body,information,actions gives the host these areas. Without it, the host knows only
// the area 'all', the minimum every cockpit has. ?recheck=100 lets the host read the manifest
// again after 100 milliseconds instead of a minute, so a test sees a new build of the module.

import { createModuleHost } from '@vanillabp/bc-module-host';

const notifications = [];
const errors = [];
const newBuilds = [];
const query = new URLSearchParams(location.search);

const host = createModuleHost({
  modules: [{ id: 'loan-approval', uri: '/wm/loan-approval' }],
  areas: query.get('areas')?.split(',').filter(Boolean),
  properties: { language: 'en', currentUser: { id: 'anna' } },
  actions: {
    'cockpit.showNotification': (args) => { notifications.push(args); },
  },
  manifestRecheckMs: query.has('recheck') ? Number(query.get('recheck')) : undefined,
  onNewBuild: (moduleId, manifest, previous) => newBuilds.push({ moduleId, from: previous.buildTimestamp, to: manifest.buildTimestamp }),
  logger: { warn: (message) => errors.push(message), error: (message) => errors.push(message) },
});

window.bcTest = {
  host,
  notifications,
  errors,
  newBuilds,
  /** Shows the form of a user task, as a cockpit does when somebody opens it. */
  async show(userTask) {
    const part = await host.showUserTask('loan-approval', userTask, document.getElementById('user-task-page'));
    return part ? [...part.elements.keys()] : undefined;
  },
};
