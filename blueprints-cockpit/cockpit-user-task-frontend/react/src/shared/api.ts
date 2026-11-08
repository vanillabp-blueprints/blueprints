// The client of the module API. Its types are generated from the OpenAPI file of the backend, see
// scripts/fetch-openapi.mjs, so a changed API breaks this build and not the running form.
//
// Every call goes to /wm/loan-approval/. The cockpit forwards that path to the workflow module.
// So the browser talks to one origin only, and the cockpit never sees the data of the form. The
// module completes the user task itself, and that is what tells the cockpit about it.

import createClient from 'openapi-fetch';
import type { components, paths } from '../generated/loan-approval.ts';
import { WORKFLOW_MODULE_ID } from '../loan-approval.ts';

export type AssessRiskForm = components['schemas']['AssessRiskForm'];
export type CheckDocumentsForm = components['schemas']['CheckDocumentsForm'];

const api = createClient<paths>({ baseUrl: `/wm/${WORKFLOW_MODULE_ID}` });

/** An answer of the module API which is not a success. */
export class ApiError extends Error {
  constructor(readonly status: number) {
    super(`The module API answered ${status}`);
  }
}

function bodyOfSuccess<T>({ data, response }: { data?: T; response: Response }): T {
  if (!response.ok) throw new ApiError(response.status);
  return data as T;
}

function requireSuccess({ response }: { response: Response }) {
  if (!response.ok) throw new ApiError(response.status);
}

const path = (userTaskId: string) => ({ params: { path: { userTaskId } } });

export const loanApprovalApi = {
  getAssessRiskForm: async (userTaskId: string) =>
    bodyOfSuccess(await api.GET('/api/user-tasks/{userTaskId}/assess-risk', path(userTaskId))),
  completeAssessRisk: async (userTaskId: string, riskAssessment: string) =>
    requireSuccess(await api.POST('/api/user-tasks/{userTaskId}/assess-risk', { ...path(userTaskId), body: { riskAssessment } })),
  getCheckDocumentsForm: async (userTaskId: string) =>
    bodyOfSuccess(await api.GET('/api/user-tasks/{userTaskId}/check-documents', path(userTaskId))),
  completeCheckDocuments: async (userTaskId: string, documentsNote: string) =>
    requireSuccess(await api.POST('/api/user-tasks/{userTaskId}/check-documents', { ...path(userTaskId), body: { documentsNote } })),
};
