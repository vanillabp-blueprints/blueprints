// The names this module shares with its backend and with the cockpit. Plain constants and the
// helper of the kit for the names of the web components: the data entry imports this file, and a
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

/**
 * The visual entry of the case page with the process. It is a file of its own, loaded only for an
 * open case. It loads bpmn-js only when it draws the process, see src/pages/draw-process.ts.
 */
export const LOAN_APPROVAL_PROCESS_PAGE = 'LoanApprovalProcessPage';

/**
 * Builds the names of the web components: the prefix of the module id, the id of this build, and
 * then the BPMN ids. The prefix keeps them apart from the names of other modules, and the build id
 * from the names of an earlier build of this module, so a new build loads without a reload of the
 * page.
 */
export const elementName = createElementNames(WORKFLOW_MODULE_ID, BUILD_ID);

/**
 * The one web component of the case page, `loan-approval-<build id>-loan-approval--process`. It
 * is the area 'all'. A cockpit with areas of its own shows it as well, because the page offers
 * none of them.
 */
export const LOAN_APPROVAL_PROCESS_ELEMENT = elementName.workflow(BPMN_PROCESS_ID, 'process');
