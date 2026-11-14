// Data entry WorkflowPage: the one entry the cockpit asks for the page of every case. It looks at
// the process and names the visual entry of the page and its web components as strings. It
// imports no page, so loading it loads no React.
//
// A cockpit with the areas 'body', 'information' and 'actions' shows those three web components.
// A cockpit without areas shows the one of 'all'.

import type { GuiApiData, WorkflowPageFunction } from '@vanillabp/bc-module-kit';
import { BPMN_PROCESS_ID, LOAN_APPROVAL_PAGE, LOAN_APPROVAL_PAGE_ELEMENTS } from '../loan-approval.ts';

/** undefined tells the cockpit that this case is not one of this module. */
export const workflowPage: WorkflowPageFunction<GuiApiData> = (workflow) => {
  if (workflow.bpmnProcessId !== BPMN_PROCESS_ID) return undefined;
  return { entry: LOAN_APPROVAL_PAGE, element: LOAN_APPROVAL_PAGE_ELEMENTS };
};
