// The client of the module API. The OpenAPI Generator `typescript-fetch` writes it from the
// OpenAPI file of the backend into src/generated/loan-approval, see `npm run generate:client`. So
// a changed API breaks this build and not the running form. Its names come from the OpenAPI file,
// as they do on the Java side: the tag `start-form` becomes the class StartFormApi, the operation
// `requestLoan` its method, and each schema a type of the same name.
//
// Every call goes to /wm/loan-approval/. That is the server of the OpenAPI file, and the generator
// makes it the base path of the client. The cockpit forwards the path to the workflow module. So
// the browser talks to one origin only. The module starts the process itself, and the cockpit
// learns about the new case from the cockpit adapter, like about every other case.
//
// An answer which is not a success throws a ResponseError, which carries the response.

import { StartFormApi } from '../generated/loan-approval/index.ts';

export { ResponseError, type LoanRequest, type StartedLoanRequest } from '../generated/loan-approval/index.ts';

export const startFormApi = new StartFormApi();
