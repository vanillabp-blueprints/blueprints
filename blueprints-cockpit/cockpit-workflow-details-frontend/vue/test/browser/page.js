// The script of the test page. It puts the host of the module kit into the page, the way a
// cockpit loads a workflow module, and hands the tests what they need.
//
// ?areas=body,information,actions gives the host these areas. Without it, the host knows only
// the area 'all', the minimum every cockpit has.
//
// The host answers 'workflow.getUserTasks' the way a cockpit does, with the user tasks a test
// put into window.bcTest.userTasks. It keeps every request it got.

import { createModuleHost } from '@vanillabp/bc-module-host';

const notifications = [];
const errors = [];
const userTaskRequests = [];
const query = new URLSearchParams(location.search);

const bcTest = {
  notifications,
  errors,
  userTaskRequests,
  /** What the cockpit answers to getUserTasks. A test sets it. */
  userTasks: [],
};

const host = createModuleHost({
  modules: [{ id: 'loan-approval', uri: '/wm/loan-approval' }],
  areas: query.get('areas')?.split(',').filter(Boolean),
  properties: { language: 'en', currentUser: { id: 'anna' } },
  actions: {
    'cockpit.showNotification': (args) => { notifications.push(args); },
    'workflow.getUserTasks': (args) => {
      userTaskRequests.push(args);
      return bcTest.userTasks;
    },
  },
  logger: { warn: (message) => errors.push(message), error: (message) => errors.push(message) },
});

bcTest.host = host;
/** Shows the page of a case, as a cockpit does when somebody opens it. */
bcTest.show = async (workflow) => {
  bcTest.part = await host.showWorkflow('loan-approval', workflow, document.getElementById('workflow-page'));
  return bcTest.part ? [...bcTest.part.elements.keys()] : undefined;
};

window.bcTest = bcTest;
