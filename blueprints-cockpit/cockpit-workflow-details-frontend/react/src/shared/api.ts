// The client of the module API. The OpenAPI Generator `typescript-fetch` writes it from the OpenAPI
// file of the backend into src/generated/loan-approval, see `npm run generate:client` and
// openapitools.json. So a changed API breaks this build and not the running page. Its names come
// from the OpenAPI file, as they do on the Java side: the tag `cases` becomes the interface
// CasesApiInterface and the class CasesApi, each operation one of their methods, and each schema a
// type of the same name. The parameters of a method are positional, as in Java:
// loanApproval.withdrawLoanRequest(loanRequestId).
//
// Every call goes to /wm/loan-approval/. That is the server of the OpenAPI file, and the generator
// makes it the base path of the client. The cockpit forwards the path to the workflow module. So
// the browser talks to one origin only. The module withdraws the loan request itself, and the
// cockpit learns about it from the cockpit adapter, like about every other end.
//
// An answer which is not a success throws a ResponseError, which carries the response.

import { CasesApi, type CasesApiInterface } from '../generated/loan-approval/index.ts';

export { ResponseError, type Case } from '../generated/loan-approval/index.ts';

/** The API of the workflow module loan-approval, which the page calls. */
export const loanApproval: CasesApiInterface = new CasesApi();
