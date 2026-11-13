// The names this module shares with its backend and with the cockpit. Plain constants: the data
// entries import this file, and a data entry must not carry a framework.

/** The id of the workflow module. The cockpit serves the module below /wm/<id>/. */
export const WORKFLOW_MODULE_ID = 'loan-approval';

/** The id of the process in the BPMN file of the backend. */
export const BPMN_PROCESS_ID = 'loan_approval';

/** The task definitions of the user tasks, as the BPMN file names them. */
export const ASSESS_RISK = 'assessRisk';
export const CHECK_DOCUMENTS = 'checkDocuments';

/** The visual entries. Each one is a file of its own, so the cockpit loads only the open form. */
export const ASSESS_RISK_FORM = 'AssessRiskForm';
export const CHECK_DOCUMENTS_FORM = 'CheckDocumentsForm';

/**
 * The web components of a form: one for the area 'all', and one for each area a cockpit may
 * have on top. The names start with the module id, so they never clash with those of another
 * module.
 */
export function formElements(form: string) {
  const prefix = `${WORKFLOW_MODULE_ID}-${form}`;
  return {
    all: prefix,
    body: `${prefix}-body`,
    information: `${prefix}-information`,
    actions: `${prefix}-actions`,
  };
}

export const ASSESS_RISK_ELEMENTS = formElements('assess-risk');
export const CHECK_DOCUMENTS_ELEMENTS = formElements('check-documents');
