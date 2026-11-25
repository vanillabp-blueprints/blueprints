// The script of the test page. It puts the host of the module kit into the page, the way a
// cockpit loads a workflow module, and hands the tests what they need.
//
// ?areas=body,information,actions gives the host these areas. Without it, the host knows only
// the area 'all', the minimum every cockpit has.

import { createModuleHost } from '@vanillabp/bc-module-host';

const errors = [];
const query = new URLSearchParams(location.search);

const host = createModuleHost({
  modules: [{ id: 'loan-approval', uri: '/wm/loan-approval' }],
  areas: query.get('areas')?.split(',').filter(Boolean),
  properties: { language: 'en', currentUser: { id: 'anna' } },
  logger: { warn: (message) => errors.push(message), error: (message) => errors.push(message) },
});

const bcTest = { host, errors };
/** Shows the page of a case, as a cockpit does when somebody opens it. */
bcTest.show = async (workflow) => {
  bcTest.part = await host.showWorkflow('loan-approval', workflow, document.getElementById('workflow-page'));
  return bcTest.part ? [...bcTest.part.elements.keys()] : undefined;
};

window.bcTest = bcTest;
