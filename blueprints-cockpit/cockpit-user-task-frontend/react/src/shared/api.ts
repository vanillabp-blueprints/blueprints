// The client of the module API. The OpenAPI Generator `typescript-fetch` writes it from the
// OpenAPI file of the backend into src/generated/loan-approval, see `npm run generate:client`. So
// a changed API breaks this build and not the running form. Its names come from the OpenAPI file,
// as they do on the Java side: the tag `user-tasks` becomes the class UserTasksApi, each operation
// one of its methods, and each schema a type of the same name.
//
// Every call goes to /wm/loan-approval/. That is the server of the OpenAPI file, and the generator
// makes it the base path of the client. The cockpit forwards the path to the workflow module. So
// the browser talks to one origin only, and the cockpit never sees the data of the form. The
// module completes the user task itself, and that is what tells the cockpit about it.
//
// An answer which is not a success throws a ResponseError, which carries the response.

import { UserTasksApi } from '../generated/loan-approval/index.ts';

export { ResponseError, type AssessRiskForm, type CheckDocumentsForm } from '../generated/loan-approval/index.ts';

export const userTasksApi = new UserTasksApi();
