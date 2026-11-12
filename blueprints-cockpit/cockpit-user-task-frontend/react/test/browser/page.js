// The script of the test page. It puts the host of the module kit into the page, the way a
// cockpit loads a workflow module, and hands the tests what they need.
//
// ?areas=body,information,actions gives the host these areas. Without it, the host knows only
// the area 'all', the minimum every cockpit has.

import { createModuleHost } from '@vanillabp/bc-module-host';

const notifications = [];
const errors = [];
const query = new URLSearchParams(location.search);

const host = createModuleHost({
  modules: [{ id: 'loan-approval', uri: '/wm/loan-approval' }],
  areas: query.get('areas')?.split(',').filter(Boolean),
  properties: { language: 'en', currentUser: { id: 'anna' } },
  actions: {
    'cockpit.showNotification': (args) => { notifications.push(args); },
  },
  logger: { warn: (message) => errors.push(message), error: (message) => errors.push(message) },
});

window.bcTest = {
  host,
  notifications,
  errors,
  /** Shows the form of a user task, as a cockpit does when somebody opens it. */
  async show(userTask) {
    const part = await host.showUserTask('loan-approval', userTask, document.getElementById('user-task-page'));
    return part ? [...part.elements.keys()] : undefined;
  },
};
