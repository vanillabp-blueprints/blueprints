// Loads what the page draws: the BPMN XML of the process the case runs on, and the history of the
// case. Both come from the module API, keyed by the business id of the case, which is the id of
// the loan request.
//
// Call activities are left out. The history of a process a call activity started needs the
// history context of that call activity, see `secondaryWorkflowHistoryContext` in the OpenAPI
// file of the backend.

import type { BcWorkflow } from '@vanillabp/bc-module-kit';
import { ResponseError, loanApproval, type WorkflowHistory } from './api.ts';

export type Problem = 'notFound' | 'failed';

export const problemOf = (error: unknown): Problem =>
  (error instanceof ResponseError && error.response.status === 404 ? 'notFound' : 'failed');

/** The case knows its loan request by the business id, which is the id of the loan request. */
export const loanRequestIdOf = (workflow: BcWorkflow) => String(workflow.businessId);

export interface CaseProcess {
  bpmnXml: string;
  history: WorkflowHistory;
}

export async function loadProcess(loanRequestId: string): Promise<CaseProcess> {
  const [diagram, history] = await Promise.all([
    loanApproval.diagram({ loanRequestId }),
    loanApproval.history({ loanRequestId }),
  ]);
  // The OpenAPI file says application/xml, so the client gives the XML as a Blob.
  return { bpmnXml: await diagram.text(), history };
}
