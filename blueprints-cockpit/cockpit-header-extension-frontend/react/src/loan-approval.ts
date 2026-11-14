// The names this module shares with its backend and with the cockpit. Plain constants: the data
// entry imports this file, and a data entry must not carry a framework.

/** The id of the workflow module. The cockpit serves the module below /wm/<id>/. */
export const WORKFLOW_MODULE_ID = 'loan-approval';

/**
 * The visual entry with the button and the start form. It is a file of its own. A cockpit loads
 * it only when it draws its header, and only from the modules the user may see.
 */
export const START_FORM = 'StartForm';

/**
 * The web components of the visual entry. The names start with the module id, so they never
 * clash with those of another module.
 */
export const START_FORM_ELEMENTS = {
  /** The button "Request a loan" in the header of the cockpit. */
  button: `${WORKFLOW_MODULE_ID}-request-loan-button`,
  /** The start form, which the cockpit shows in a modal dialog. */
  form: `${WORKFLOW_MODULE_ID}-request-loan-form`,
};
