// Data entry UserTaskForm: the one entry the cockpit asks for the form of every user task. It
// looks at the process and at the task definition, and it names the visual entry of the form and
// its web components as strings. It imports no form, so loading it loads no React.
//
// A cockpit with the areas 'body', 'information' and 'actions' shows those three web components.
// A cockpit without areas shows the one of 'all'.

import type { BcPageParts, GuiApiData, UserTaskFormFunction } from '@vanillabp/bc-module-kit';
import {
  ASSESS_RISK,
  ASSESS_RISK_ELEMENTS,
  ASSESS_RISK_FORM,
  BPMN_PROCESS_ID,
  CHECK_DOCUMENTS,
  CHECK_DOCUMENTS_ELEMENTS,
  CHECK_DOCUMENTS_FORM,
} from '../loan-approval.ts';

/** The forms of the process loan_approval, by task definition. */
const loanApprovalForms: Record<string, BcPageParts> = {
  [ASSESS_RISK]: { entry: ASSESS_RISK_FORM, element: ASSESS_RISK_ELEMENTS },
  [CHECK_DOCUMENTS]: { entry: CHECK_DOCUMENTS_FORM, element: CHECK_DOCUMENTS_ELEMENTS },
};

/** undefined tells the cockpit that this user task is not one of this module. */
export const userTaskForm: UserTaskFormFunction<GuiApiData> = (userTask) => {
  if (userTask.bpmnProcessId !== BPMN_PROCESS_ID) return undefined;
  return Object.hasOwn(loanApprovalForms, userTask.taskDefinition) ? loanApprovalForms[userTask.taskDefinition] : undefined;
};
