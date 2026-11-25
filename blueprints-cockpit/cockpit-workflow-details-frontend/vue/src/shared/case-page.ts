// What the areas of the case page share: the properties of the cockpit, the case from the module
// API, the open user tasks from the cockpit, and the messages between the areas.
//
// In a cockpit with areas, the three areas are three web components, and nothing is shared
// between them but DOM events and the data this file loads once for all of them. When "Withdraw
// loan request" succeeds, 'actions' tells 'body' and 'information' with sendTo(), and they load
// their data again. The cockpit also sets the property 'workflow' again when it learns that the
// case changed, and that loads the user tasks again as well.

import { computed, ref, shallowRef, watch, type Ref } from 'vue';
import type { BcUserTask, BcWorkflow } from '@vanillabp/bc-module-kit';
import { ResponseError, loanApproval, type Case } from './api.ts';
import { textsFor } from './texts.ts';
import { useBcElement } from './vue-element.ts';

/** What one area tells another one of the same page. */
export type AreaMessage = { withdrawn?: boolean };

export type Problem = 'notFound' | 'hasEnded' | 'failed';

export const problemOf = (error: unknown): Problem => {
  if (error instanceof ResponseError && error.response.status === 404) return 'notFound';
  if (error instanceof ResponseError && error.response.status === 409) return 'hasEnded';
  return 'failed';
};

/** The areas 'information' and 'actions' both need the case. It is loaded once for both. */
const cases = new Map<string, Promise<Case>>();

function loadCase(loanRequestId: string) {
  let loading = cases.get(loanRequestId);
  if (!loading) {
    loading = loanApproval.getCase({ loanRequestId });
    loading.catch(() => cases.delete(loanRequestId));
    cases.set(loanRequestId, loading);
  }
  return loading;
}

/** After a withdrawal the case has changed, so the next area which asks loads it again. */
export function forgetCase(loanRequestId: string) {
  cases.delete(loanRequestId);
}

/** What every area has: the properties of the cockpit, the case and the texts. */
export function useCasePage() {
  const props = useBcElement();
  const workflow = computed(() => props.value.workflow as BcWorkflow);
  return {
    props,
    workflow,
    /** The case knows its loan request by the business id, which is the id of the loan request. */
    loanRequestId: computed(() => String(workflow.value.businessId)),
    texts: computed(() => textsFor(props.value.language)),
    language: computed(() => String(props.value.language ?? 'en')),
  };
}

/** The case of the loan request. It is loaded again whenever `reload` counts up. */
export function useCase(loanRequestId: Ref<string>, reload: Ref<number>) {
  const loaded = shallowRef<Case>();
  const problem = ref<Problem>();
  watch([loanRequestId, reload], ([id], _, onCleanup) => {
    let current = true;
    onCleanup(() => { current = false; });
    loadCase(id).then(
      (found) => {
        if (!current) return;
        loaded.value = found;
        problem.value = undefined;
      },
      (error) => { if (current) problem.value = problemOf(error); },
    );
  }, { immediate: true });
  return { loaded, problem };
}

/**
 * The open user tasks of the case. The page asks the cockpit for them with getUserTasks(), and
 * the cockpit reads its own list. The module has no client for the API of the cockpit.
 *
 * The first argument asks for open tasks only. The second one says whether the cockpit leaves
 * out the tasks the current user may not see. This page passes false, so everybody who sees the
 * case sees where it stands. A page which lets the user open a task from here passes true.
 */
export function useOpenUserTasks(workflow: Ref<BcWorkflow>, reload: Ref<number>) {
  const userTasks = shallowRef<BcUserTask[]>();
  const failed = ref(false);
  // The cockpit sets 'workflow' again whenever the case changes, as a new object. So the watch
  // looks at what tells such a change, not at the object.
  const changes = computed(() => [workflow.value.id, workflow.value.updatedAt, workflow.value.endedAt, reload.value].join('|'));
  watch(changes, (_, __, onCleanup) => {
    let current = true;
    onCleanup(() => { current = false; });
    workflow.value.getUserTasks(true, false).then(
      (found) => {
        if (!current) return;
        userTasks.value = found;
        failed.value = false;
      },
      () => { if (current) failed.value = true; },
    );
  }, { immediate: true });
  return { userTasks, failed };
}

/** Counts up whenever another area says that the loan request was withdrawn. */
export function useReloadOnMessage(workflow: Ref<BcWorkflow>) {
  const reload = ref(0);
  // The subscription belongs to the web component, which stays the same while the cockpit sets
  // 'workflow' again. So it is made once per case.
  const workflowId = computed(() => workflow.value.id);
  watch(workflowId, (_, __, onCleanup) => {
    const subscription = workflow.value.receiveMessage((message) => {
      if ((message as AreaMessage | undefined)?.withdrawn) reload.value += 1;
    });
    onCleanup(() => subscription.unsubscribe());
  }, { immediate: true });
  return reload;
}
