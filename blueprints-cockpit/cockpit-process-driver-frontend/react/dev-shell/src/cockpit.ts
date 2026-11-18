// The parts of the cockpit the dev shell plays: the host which loads the micro-frontends of the
// module, and the calls to the simulated cockpit.
//
// The simulated cockpit is the dev shell simulator of the Business Cockpit. The workflow module
// reports its workflows and user tasks there, as it would to a real cockpit. The dev shell reads
// them through the official API of the cockpit below /official-api/gui/api/v1: the simulator
// answers each system it plays below a prefix of its own, and below it the path of the system.
// The calls go through the clients generated from the OpenAPI file of that API, see
// @official-gui-client.

import { createModuleHost } from '@vanillabp/bc-module-host';
import { WORKFLOW_MODULE_ID } from '@loan-approval';
import { ResponseError, tasklist, workflowlist, type UserTask, type Workflow } from '@official-gui-client';

/** A workflow as the cockpit lists it. */
export type CockpitWorkflow = Workflow;

/** A user task as the cockpit lists it. The host hands all of it to the form. */
export type CockpitUserTask = UserTask;

/** The simulated cockpit answered, but not with a success. */
export class CockpitError extends Error {
  constructor(readonly status: number) {
    super(`The simulated cockpit answered ${status}. Is the simulator of the backend running?`);
    this.name = 'CockpitError';
  }
}

/** One call to the official API. 404 means that the cockpit does not know the item (yet). */
async function call<T>(answer: Promise<T>): Promise<T | undefined> {
  try {
    return await answer;
  } catch (error) {
    if (!(error instanceof ResponseError)) throw error;
    if (error.response.status === 404) return undefined;
    throw new CockpitError(error.response.status);
  }
}

export const cockpit = {
  /** The workflow of a business id. The business id of a loan approval is its loan request id. */
  workflowOf: async (businessId: string) => {
    const answer = await call(workflowlist.getWorkflows({
      workflowsRequest: { businessIds: [businessId], mode: 'All', pageNumber: 0, pageSize: 1 },
    }));
    return answer?.workflows?.[0];
  },
  workflow: (workflowId: string) => call(workflowlist.getWorkflow({ workflowId })),
  userTasksOf: (workflowId: string) => call(workflowlist.getUserTasksOfWorkflow({
    workflowId,
    llatcup: false,
    userTasksRequest: { mode: 'All', pageNumber: 0, pageSize: 50 },
  })),
  userTask: (userTaskId: string) => call(tasklist.getUserTask({ userTaskId })),
};

/**
 * Asks again until there is an answer. The module reports to the cockpit after its transaction
 * committed, so the cockpit knows a new workflow or user task a moment later.
 */
export async function waitFor<T>(read: () => Promise<T | undefined>, timeoutMs = 10_000, intervalMs = 250): Promise<T | undefined> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const found = await read();
    if (found !== undefined || Date.now() >= deadline) return found;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
}

/** The title in the language of the dev shell, or any title there is. */
export const titleOf = (item: { title?: Record<string, string> }, fallback: string) => item.title?.en ?? Object.values(item.title ?? {})[0] ?? fallback;

/** What the forms reported with showNotification, newest last. */
export const notifications: Array<{ level?: string; text?: string }> = [];
const listeners = new Set<() => void>();
export const onNotification = (listener: () => void) => {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
};

/**
 * The host of the module kit loads the micro-frontends of this project the way a cockpit does:
 * the manifest below /wm/loan-approval/, then the data entry UserTaskForm, then the form. The dev
 * shell has the areas 'body' and 'actions', like a cockpit with areas.
 */
export const host = createModuleHost({
  modules: [{ id: WORKFLOW_MODULE_ID, uri: `/wm/${WORKFLOW_MODULE_ID}` }],
  areas: ['body', 'actions'],
  properties: { language: 'en', currentUser: { id: 'jane' } },
  actions: {
    'cockpit.showNotification': (args) => {
      notifications.push(args);
      listeners.forEach((listener) => listener());
    },
  },
});
