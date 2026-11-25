// The client of the module API. @hey-api/openapi-ts writes it from the OpenAPI file of the backend
// into src/generated/loan-approval, see `npm run generate:client` and openapi-ts.config.mjs. It is
// an npm package and runs on Node only, so the build needs no Java. A changed API breaks this
// build and not the running page. Its names come from the OpenAPI file, as they do on the Java
// side: the tag `loan-approval` becomes the class LoanApprovalApi, each operation one of its
// methods, and each schema a type of the same name. A method takes one object with all its
// parameters: loanApproval.history({ loanRequestId }).
//
// The page calls two of the endpoints of bpmn-history-and-diagram: `diagram`, the BPMN XML of the
// process the case runs on, and `history`, what the case has done. Every call goes to
// /wm/loan-approval/api/, see openapi-ts.config.mjs. The cockpit forwards the path to the
// workflow module. So the browser talks to one origin only.

import { client, LoanApprovalApi } from '@generated';

export type { WorkflowElementHistory, WorkflowHistory } from '@generated';

/**
 * An answer which is not a success. The generated client throws only the body of such an answer,
 * without its status, and the OpenAPI file gives errors no body. So without this class the page
 * could not tell 404 from any other failure.
 */
export class ResponseError extends Error {
  readonly response: Response;
  readonly body: unknown;

  constructor(response: Response, body: unknown) {
    super(`${response.status} ${response.statusText}`);
    this.name = 'ResponseError';
    this.response = response;
    this.body = body;
  }
}

// Turns every answer which is not a success into a ResponseError. A network error has no answer
// and stays what fetch threw.
client.interceptors.error.use((error, response) => (response && !response.ok ? new ResponseError(response, error) : error));

/** The API of the workflow module loan-approval, which the page calls. */
export const loanApproval = new LoanApprovalApi();
