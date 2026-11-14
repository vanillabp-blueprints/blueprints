// Both user tasks of this module have a form of the same kind: some data to read, one input field
// and a button "Complete". This file and the components in input-form/ build the four web
// components of such a form, so each form file only says what is different.
//
//   all          the whole form in one web component, for a cockpit without areas
//   body         the input field
//   information  the data to read
//   actions      the button "Complete"
//
// In a cockpit with areas, the three areas are three web components, and nothing is shared
// between them but DOM events. The input field is in 'body' and the button in 'actions'. So
// 'body' tells 'actions' the value of the field with sendTo(), and 'actions' gets it with
// receiveMessage(). The button is enabled once the field is filled.
//
// The components are the same for both forms. What differs comes in through provide() under the
// key INPUT_FORM, which defineInputForm() sets for the web components of one form.

import { computed, inject, ref, shallowRef, watch, type Component, type InjectionKey } from 'vue';
import type { BcUserTask } from '@vanillabp/bc-module-kit';
import { ApiError } from './api.ts';
import { textsFor, type Texts } from './texts.ts';
import { defineVueElement, useBcElement } from './vue-element.ts';
import ActionsArea from './input-form/ActionsArea.ce.vue';
import AllArea from './input-form/AllArea.ce.vue';
import BodyArea from './input-form/BodyArea.ce.vue';
import InformationArea from './input-form/InformationArea.ce.vue';

export interface InputForm<Form> {
  /** The start of every data-testid of the form, for example 'assess-risk'. */
  testId: string;
  /** The label of the input field. */
  label: (texts: Texts) => string;
  /** Loads the data of the form from the module API. */
  load: (userTaskId: string) => Promise<Form>;
  /** The value the input field starts with. */
  initialInput: (form: Form) => string;
  /** Completes the user task with the value of the input field. */
  complete: (userTaskId: string, input: string) => Promise<void>;
  /** What the area 'information' shows: a component with the properties form, texts and language. */
  information: Component;
}

/** What one area tells another one of the same form. */
export type AreaMessage = { input?: string; askForInput?: boolean; completed?: boolean };

export type Problem = 'notFound' | 'failed';

const problemOf = (error: unknown): Problem => (error instanceof ApiError && error.status === 404 ? 'notFound' : 'failed');

/** One form: its definition, and the data of its user tasks, loaded once for all its areas. */
interface InputFormOfModule {
  definition: InputForm<unknown>;
  cache: Map<string, Promise<unknown>>;
}

const INPUT_FORM: InjectionKey<InputFormOfModule> = Symbol('InputForm');

/** The areas 'body' and 'information' both need the data. It is loaded once for both. */
function loadOnce({ definition, cache }: InputFormOfModule, userTaskId: string) {
  let loading = cache.get(userTaskId);
  if (!loading) {
    loading = definition.load(userTaskId);
    loading.catch(() => cache.delete(userTaskId));
    cache.set(userTaskId, loading);
  }
  return loading;
}

/** What every area has: the properties of the cockpit, the definition of the form, the texts. */
export function useArea() {
  const props = useBcElement();
  const inputForm = inject(INPUT_FORM)!;
  const userTask = computed(() => props.value.userTask as BcUserTask);
  return {
    props,
    inputForm,
    definition: inputForm.definition,
    testId: inputForm.definition.testId,
    userTask,
    /**
     * The cockpit hands over a new user task object whenever it sets any property, the language
     * for example. The id stays the same then, so whatever watches the id only does not run again.
     */
    userTaskId: computed(() => userTask.value.id!),
    texts: computed(() => textsFor(props.value.language)),
    language: computed(() => String(props.value.language ?? 'en')),
  };
}

/**
 * The data of the form. They are loaded when the id of the user task changes.
 *
 * @param loaded called once the data are there
 */
export function useFormData(area: ReturnType<typeof useArea>, loaded: (form: unknown) => void = () => {}) {
  const form = shallowRef<unknown>();
  const loadProblem = ref<Problem>();
  watch(area.userTaskId, (userTaskId, _, onCleanup) => {
    let current = true;
    onCleanup(() => { current = false; });
    loadOnce(area.inputForm, userTaskId).then(
      (data) => {
        if (!current) return;
        form.value = data;
        loaded(data);
      },
      (error) => { if (current) loadProblem.value = problemOf(error); },
    );
  }, { immediate: true });
  return { form, loadProblem };
}

/** Completes the user task and tells the cockpit through a notification. */
export function useCompletion(area: ReturnType<typeof useArea>) {
  const state = ref<'open' | 'running' | 'completed'>('open');
  const problem = ref<Problem>();
  async function complete(input: string) {
    const { userTask, showNotification, language } = area.props.value;
    const texts = textsFor(language);
    state.value = 'running';
    problem.value = undefined;
    try {
      await area.definition.complete(userTask!.id!, input.trim());
      area.inputForm.cache.delete(userTask!.id!);
      state.value = 'completed';
      // A cockpit which cannot show notifications refuses; the form says it anyway.
      showNotification({ level: 'success', text: texts.completed }).catch(() => {});
      return true;
    } catch (error) {
      state.value = 'open';
      problem.value = problemOf(error);
      showNotification({ level: 'error', text: texts[problemOf(error)] }).catch(() => {});
      return false;
    }
  }
  return { state, problem, complete };
}

export function defineInputForm<Form>(elements: { all: string; body: string; information: string; actions: string }, definition: InputForm<Form>) {
  const inputForm: InputFormOfModule = { definition: definition as InputForm<unknown>, cache: new Map() };
  const provides: [InjectionKey<unknown>, unknown][] = [[INPUT_FORM, inputForm]];
  defineVueElement(elements.all, AllArea, provides);
  defineVueElement(elements.body, BodyArea, provides);
  defineVueElement(elements.information, InformationArea, provides);
  defineVueElement(elements.actions, ActionsArea, provides);
}
