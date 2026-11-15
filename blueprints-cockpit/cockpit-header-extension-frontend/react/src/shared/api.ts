// The client of the module API. The OpenAPI Generator `typescript-fetch` writes it from the OpenAPI
// file of the backend into src/generated/loan-approval, see `npm run generate:client` and
// openapitools.json. So a changed API breaks this build and not the running form. Its names come
// from the OpenAPI file, as they do on the Java side: the tag `start-form` becomes the interface
// StartFormApiInterface and the class StartFormApi, each operation one of their methods, and each
// schema a type of the same name. The parameters of a method are positional, as in Java:
// loanApproval.requestLoan({ applicant, amount }).
//
// Every call goes to /wm/loan-approval/. That is the server of the OpenAPI file, and the generator
// makes it the base path of the client. The cockpit forwards the path to the workflow module. So
// the browser talks to one origin only. The module starts the process itself, and the cockpit
// learns about the new case from the cockpit adapter, like about every other case.
//
// An answer which is not a success throws a ResponseError, which carries the response.

import { StartFormApi, type StartFormApiInterface } from '../generated/loan-approval/index.ts';

export { ResponseError, type LoanRequest, type StartedLoanRequest } from '../generated/loan-approval/index.ts';

/** The API of the workflow module loan-approval, which the start form calls. */
export const loanApproval: StartFormApiInterface = new StartFormApi();
