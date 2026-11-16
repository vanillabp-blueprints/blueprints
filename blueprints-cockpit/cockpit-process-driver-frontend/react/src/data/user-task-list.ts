// Data entry UserTaskList: the columns this module adds to the task list of the cockpit. Plain
// JavaScript, it imports no framework.
//
// The values come from the backend. Its @UserTaskDetailsProvider puts the amount and the credit
// score into the 'details' of every user task. The cockpit reads them there by the path of the
// column.

import type { ColumnsOfUserTaskFunction, GuiApiData } from '@vanillabp/bc-module-kit';
import { AMOUNT, BPMN_PROCESS_ID, CREDIT_SCORE } from '@loan-approval';

export const userTaskListColumns: ColumnsOfUserTaskFunction<GuiApiData> = (userTask) => {
  if (userTask.bpmnProcessId !== BPMN_PROCESS_ID) return undefined;
  const column = { show: true, sortable: true, filterable: false, resizeable: true, width: '10rem' };
  return [
    { ...column, title: { en: 'Amount', de: 'Betrag' }, path: `details.${AMOUNT}`, priority: 10 },
    { ...column, title: { en: 'Credit score', de: 'Bonität' }, path: `details.${CREDIT_SCORE}`, priority: 20 },
  ];
};
