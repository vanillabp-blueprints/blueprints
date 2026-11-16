// The client of the module API. @hey-api/openapi-ts writes it from the OpenAPI file of the backend
// into src/generated/loan-approval, see `npm run generate:client` and openapi-ts.config.mjs. It is
// an npm package and runs on Node only, so the build needs no Java. A changed API breaks this
// build and not the running form. Its names come from the OpenAPI file, as they do on the Java
// side: the tag `user-tasks` becomes the class UserTasksApi, each operation one of its methods,
// and each schema a type of the same name. A method takes one object with all its parameters,
// and the body comes under the name of its schema:
// loanApproval.completeAssessRisk({ userTaskId, assessRiskInput: { riskAssessment } }).
//
// Every call goes to /wm/loan-approval/. That is the server of the OpenAPI file, and the generator
// makes it the base URL of the client. The cockpit forwards the path to the workflow module. So
// the browser talks to one origin only, and the cockpit never sees the data of the form. The
// module completes the user task itself, and that is what tells the cockpit about it.

import { client } from '../generated/loan-approval/client.gen.ts';
import { UserTasksApi } from '../generated/loan-approval/index.ts';

export type { AssessRiskForm, CheckDocumentsForm } from '../generated/loan-approval/index.ts';

/**
 * An answer which is not a success. The generated client throws only the body of such an answer,
 * without its status, and the OpenAPI file gives errors no body. So without this class the form
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

/** The API of the workflow module loan-approval, which the forms call. */
export const loanApproval = new UserTasksApi();
