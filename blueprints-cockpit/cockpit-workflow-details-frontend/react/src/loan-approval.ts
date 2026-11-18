// The names this module shares with its backend and with the cockpit. Plain constants and the
// helper of the kit for the names of the web components: the data entries import this file, and a
// data entry must not carry a framework.

import { createElementNames } from '@vanillabp/bc-module-kit';
import { BUILD_ID, WORKFLOW_MODULE_ID } from './bc-build.js';

/**
 * The id of the workflow module. The cockpit serves the module below /wm/<id>/. It comes from
 * `config.workflowModuleId` in package.json, through src/bc-build.js.
 */
export { WORKFLOW_MODULE_ID };

/** The id of the process in the BPMN file of the backend. */
export const BPMN_PROCESS_ID = 'loan_approval';

/** The visual entry of the case page. It is a file of its own, loaded only for an open case. */
export const LOAN_APPROVAL_PAGE = 'LoanApprovalPage';

/**
 * Builds the names of the web components: the prefix of the module id, the id of this build, and
 * then the BPMN ids. The prefix keeps them apart from the names of other modules, and the build id
 * from the names of an earlier build of this module, so a new build loads without a reload of the
 * page.
 */
export const elementName = createElementNames(WORKFLOW_MODULE_ID, BUILD_ID);

/**
 * The web components of the case page: one for the area 'all', and one for each area a cockpit
 * may have on top. The one of 'all' is `loan-approval-<build id>-loan-approval--page`.
 */
export const LOAN_APPROVAL_PAGE_ELEMENTS = {
  all: elementName.workflow(BPMN_PROCESS_ID, 'page'),
  body: elementName.workflow(BPMN_PROCESS_ID, 'body'),
  information: elementName.workflow(BPMN_PROCESS_ID, 'information'),
  actions: elementName.workflow(BPMN_PROCESS_ID, 'actions'),
};

/** The keys the backend puts into the details of a case, see its WorkflowTaskHandler. */
export const DETAILS_APPLICANT = 'applicant';
export const DETAILS_STATUS = 'status';
