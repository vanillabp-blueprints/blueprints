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

/** The task definitions of the user tasks, as the BPMN file names them. */
export const ASSESS_RISK = 'assessRisk';
export const CHECK_DOCUMENTS = 'checkDocuments';

/** The visual entries. Each one is a file of its own, so the cockpit loads only the open form. */
export const ASSESS_RISK_FORM = 'AssessRiskForm';
export const CHECK_DOCUMENTS_FORM = 'CheckDocumentsForm';

/**
 * Builds the names of the web components: the prefix of the module id, the id of this build, and
 * then the BPMN ids. The prefix keeps them apart from the names of other modules, and the build id
 * from the names of an earlier build of this module, so a new build loads without a reload of the
 * page.
 */
export const elementName = createElementNames(WORKFLOW_MODULE_ID, BUILD_ID);

/**
 * The web components of the form of a user task: one for the area 'all', and one for each area a
 * cockpit may have on top. For "Assess risk" the one of 'all' is
 * `loan-approval-<build id>-loan-approval--assess-risk--form`.
 */
export function formElements(taskDefinition: string) {
  return {
    all: elementName.userTask(BPMN_PROCESS_ID, taskDefinition, 'form'),
    body: elementName.userTask(BPMN_PROCESS_ID, taskDefinition, 'body'),
    information: elementName.userTask(BPMN_PROCESS_ID, taskDefinition, 'information'),
    actions: elementName.userTask(BPMN_PROCESS_ID, taskDefinition, 'actions'),
  };
}

export const ASSESS_RISK_ELEMENTS = formElements(ASSESS_RISK);
export const CHECK_DOCUMENTS_ELEMENTS = formElements(CHECK_DOCUMENTS);
