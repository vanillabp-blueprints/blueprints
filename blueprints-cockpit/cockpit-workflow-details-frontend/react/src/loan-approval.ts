// The names this module shares with its backend and with the cockpit. Plain constants: the data
// entries import this file, and a data entry must not carry a framework.

/** The id of the workflow module. The cockpit serves the module below /wm/<id>/. */
export const WORKFLOW_MODULE_ID = 'loan-approval';

/** The id of the process in the BPMN file of the backend. */
export const BPMN_PROCESS_ID = 'loan_approval';

/** The visual entry of the case page. It is a file of its own, loaded only for an open case. */
export const LOAN_APPROVAL_PAGE = 'LoanApprovalPage';

/**
 * The web components of the case page: one for the area 'all', and one for each area a cockpit
 * may have on top. The names start with the module id, so they never clash with those of
 * another module.
 */
export const LOAN_APPROVAL_PAGE_ELEMENTS = {
  all: `${WORKFLOW_MODULE_ID}-case`,
  body: `${WORKFLOW_MODULE_ID}-case-body`,
  information: `${WORKFLOW_MODULE_ID}-case-information`,
  actions: `${WORKFLOW_MODULE_ID}-case-actions`,
};

/** The keys the backend puts into the details of a case, see its WorkflowTaskHandler. */
export const DETAILS_APPLICANT = 'applicant';
export const DETAILS_OUTCOME = 'outcome';
