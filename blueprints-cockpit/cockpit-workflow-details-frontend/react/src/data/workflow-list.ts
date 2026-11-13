// Data entry WorkflowList: the columns this module adds to the case list of the cockpit. Plain
// JavaScript, it imports no framework.
//
// The values come from the backend. Its @WorkflowDetailsProvider puts the applicant into the
// 'details' of every report of a case, and the outcome once the case ended. The cockpit reads
// them there by the path of the column.
//
// The outcome tells a cancelled case from an approved one. The cockpit cannot tell them apart by
// itself: the cancelled case ends at the end event of the event subprocess, so the cockpit
// adapter reports a regular end. The README says more.

import type { ColumnsOfWorkflowFunction, GuiApiData } from '@vanillabp/bc-module-kit';
import { BPMN_PROCESS_ID, DETAILS_APPLICANT, DETAILS_OUTCOME } from '../loan-approval.ts';

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
      title: { en: 'Outcome', de: 'Ergebnis' },
      path: `details.${DETAILS_OUTCOME}`,
      priority: 20,
      width: '10rem',
      show: true,
      sortable: true,
      filterable: true,
      resizeable: true,
    },
  ];
};
