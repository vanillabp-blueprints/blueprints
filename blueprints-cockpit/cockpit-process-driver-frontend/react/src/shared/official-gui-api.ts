// The clients of the official API of the cockpit. Only the dev shell calls them, never a form, so
// they have an alias of their own: a form which imports @gui-client does not carry them.
// @hey-api/openapi-ts writes them into src/generated/official-gui-api, from the OpenAPI file of the
// cockpit, which scripts/fetch-openapi.mjs takes out of the cockpit's jar official-gui-api-server.
//
// The dev shell simulator of the Business Cockpit answers the official API below
// /official-api/gui/api/v1, and the dev shell forwards /official-api/ to it.

import { client, OfficialTasklistApi, OfficialWorkflowlistApi } from '@generated-official-gui-api';
import { throwResponseErrors } from './response-error.ts';

export type { UserTask, Workflow } from '@generated-official-gui-api';
export { ResponseError } from './response-error.ts';

throwResponseErrors(client);

/** The task list of the cockpit. */
export const tasklist = new OfficialTasklistApi();

/** The list of workflows of the cockpit. */
export const workflowlist = new OfficialWorkflowlistApi();
