// Data entry UserTaskList: the columns this module adds to the task list of the cockpit. Plain
// JavaScript, it imports no framework.
//
// The value comes from the backend. When "Assess risk" is completed, the backend stores the risk
// assessment in the workflow aggregate, and its @UserTaskDetailsProvider puts it into the
// 'details' of every later user task. The cockpit reads it there by the path of the column.

import type { ColumnsOfUserTaskFunction, GuiApiData } from '@vanillabp/bc-module-kit';
import { BPMN_PROCESS_ID } from '../loan-approval.ts';

export const userTaskListColumns: ColumnsOfUserTaskFunction<GuiApiData> = (userTask) => {
  if (userTask.bpmnProcessId !== BPMN_PROCESS_ID) return undefined;
  return [
    {
      title: { en: 'Risk assessment', de: 'Risikobewertung' },
      path: 'details.riskAssessment',
      priority: 10,
      width: '16rem',
      show: true,
      sortable: true,
      filterable: true,
      resizeable: true,
    },
  ];
};
