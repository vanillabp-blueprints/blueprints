// The client of the module API. Its types are generated from the OpenAPI file of the backend, see
// scripts/fetch-openapi.mjs, so a changed API breaks this build and not the running form.
//
// Every call goes to /wm/loan-approval/. That is the server of the OpenAPI file, and
// scripts/fetch-openapi.mjs writes it to src/generated/server.ts. The cockpit forwards the path
// to the workflow module. So the browser talks to one origin only. The module starts the process
// itself, and the cockpit learns about the new case from the cockpit adapter, like about every
// other case.

import createClient from 'openapi-fetch';
import type { components, paths } from '../generated/loan-approval.ts';
import { SERVER_URL } from '../generated/server.ts';

export type LoanRequest = components['schemas']['LoanRequest'];
export type StartedLoanRequest = components['schemas']['StartedLoanRequest'];

const api = createClient<paths>({ baseUrl: SERVER_URL });

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

export const loanApprovalApi = {
  requestLoan: async (loanRequest: LoanRequest) =>
    bodyOfSuccess(await api.POST('/api/loan-requests', { body: loanRequest })),
};
