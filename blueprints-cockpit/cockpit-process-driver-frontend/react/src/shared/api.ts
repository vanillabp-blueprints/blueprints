// The client of the module API, which the forms call. @hey-api/openapi-ts writes it from the
// OpenAPI file of the backend into src/generated/loan-approval, see `npm run generate:client` and
// openapi-ts.config.mjs. A changed API breaks this build and not the running form. Its names come
// from the OpenAPI file, as they do on the Java side: the tag `loan-approval` becomes the class
// LoanApprovalApi, and each operation one of its methods. A method takes one object with all its
// parameters: loanApproval.completeAssessRisk({ loanRequestId, taskId }).
//
// A form completes its user task with POST, completeAssessRisk and completeCheckContract. The
// GET requests on the same paths are there for a person who walks through the process in a
// browser, see CONTRIBUTING.md.
//
// Every call goes to /wm/loan-approval/. The cockpit forwards the path to the workflow module. So
// the browser talks to one origin only. The module completes the user task itself, and that is
// what tells the cockpit about it.

import { client, LoanApprovalApi } from '@generated';
import { throwResponseErrors } from './response-error.ts';

export { ResponseError } from './response-error.ts';

throwResponseErrors(client);

/** The API of the workflow module loan-approval, which the forms call. */
export const loanApproval = new LoanApprovalApi();
