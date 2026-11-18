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

/**
 * The visual entry with the button and the start form. It is a file of its own. A cockpit loads
 * it only when it draws its header, and only from the modules the user may see.
 */
export const START_FORM = 'StartForm';

/**
 * Builds the names of the web components: the prefix of the module id, the id of this build, and
 * then a short name. The prefix keeps them apart from the names of other modules, and the build id
 * from the names of an earlier build of this module, so a new build loads without a reload of the
 * page.
 */
export const elementName = createElementNames(WORKFLOW_MODULE_ID, BUILD_ID);

/**
 * The web components of the visual entry. Neither shows a workflow or a user task, so their short
 * names are custom ones: the button is `loan-approval-<build id>-request-loan-button`.
 */
export const START_FORM_ELEMENTS = {
  /** The button "Request a loan" in the header of the cockpit. */
  button: elementName.custom('request-loan-button'),
  /** The start form, which the cockpit shows in a modal dialog. */
  form: elementName.custom('request-loan-form'),
};
