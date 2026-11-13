// Data entry WorkflowList: the columns this module adds to the case list of the cockpit. Plain
// JavaScript, it imports no framework.
//
// The values come from the backend. Its @WorkflowDetailsProvider puts the applicant into the
// 'details' of every report of a case, and the status of the loan request. The cockpit reads
// them there by the path of the column.
//
// The status tells a withdrawn loan request from an approved one. Both cases end normally, at an
// end event of the process, because withdrawing is a business decision. The README says more.

import type { ColumnsOfWorkflowFunction, GuiApiData } from '@vanillabp/bc-module-kit';
import { BPMN_PROCESS_ID, DETAILS_APPLICANT, DETAILS_STATUS } from '../loan-approval.ts';

export const workflowListColumns: ColumnsOfWorkflowFunction<GuiApiData> = (workflow) => {
  if (workflow.bpmnProcessId !== BPMN_PROCESS_ID) return undefined;
  return [
    {
      title: { en: 'Applicant', de: 'Antragsteller' },
      path: `details.${DETAILS_APPLICANT}`,
      priority: 10,
      width: '14rem',
      show: true,
      sortable: true,
      filterable: true,
      resizeable: true,
    },
    {
      title: { en: 'Status', de: 'Status' },
      path: `details.${DETAILS_STATUS}`,
      priority: 20,
      width: '10rem',
      show: true,
      sortable: true,
      filterable: true,
      resizeable: true,
    },
  ];
};
