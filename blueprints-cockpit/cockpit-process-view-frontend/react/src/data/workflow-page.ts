// Data entry WorkflowPage: the one entry the cockpit asks for the page of every case. It looks at
// the process and names the visual entry of the page and its web component as strings. It imports
// no page, so loading it loads neither the framework nor bpmn-js.

import type { GuiApiData, WorkflowPageFunction } from '@vanillabp/bc-module-kit';
import { BPMN_PROCESS_ID, LOAN_APPROVAL_PROCESS_ELEMENT, LOAN_APPROVAL_PROCESS_PAGE } from '@loan-approval';

/** undefined tells the cockpit that this case is not one of this module. */
export const workflowPage: WorkflowPageFunction<GuiApiData> = (workflow) => {
  if (workflow.bpmnProcessId !== BPMN_PROCESS_ID) return undefined;
  return { entry: LOAN_APPROVAL_PROCESS_PAGE, element: LOAN_APPROVAL_PROCESS_ELEMENT };
};
