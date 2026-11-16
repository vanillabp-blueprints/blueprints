// The client of the process driver. Only the dev shell calls it, never a form, so it has an
// alias of its own: a form which imports @gui-client does not carry it. @hey-api/openapi-ts writes
// it from the OpenAPI file of the driver into src/generated/process-driver.
//
// The backend has the driver only in a build with the Maven profile `test-driver`, and only while
// the property `process-driver.enabled` is `true`. Without them every call answers 404.

import { client, ProcessDriverApi } from '@generated-process-driver';
import { throwResponseErrors } from './response-error.ts';

export type { Reached } from '@generated-process-driver';
export { ResponseError } from './response-error.ts';

throwResponseErrors(client);

/** The process driver of the workflow module loan-approval. */
export const processDriver = new ProcessDriverApi();
