// Both user tasks of this module have a form of the same kind: some data to read, one input field
// and a button "Complete". This file holds the four web components of such a form, so each form
// file only says what is different.
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
// The components are the same for both forms. What differs comes in through InjectionToken
// INPUT_FORM, which defineInputForm() provides for the web components of one form.

import { Component, computed, Directive, effect, inject, InjectionToken, input, signal, untracked, type Type } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import type { BcElementProps, BcUserTask } from '@vanillabp/bc-module-kit';
import { ResponseError } from './api.ts';
import { defineAngularElement } from './angular-element.ts';
import { textsFor, type Texts } from './texts.ts';

export interface InputForm<Form> {
  /** The start of every data-testid of the form, for example 'assess-risk'. */
  testId: string;
  /** The label of the input field. */
  label: (texts: Texts) => string;
  /**
   * Loads the data of the form from the module API. A call names the loan request, which is the
   * business id of the user task, and the user task. The module checks that they belong together.
   */
  load: (loanRequestId: string, taskId: string) => Promise<Form>;
  /** The value the input field starts with. */
  initialInput: (form: Form) => string;
  /** Completes the user task with the value of the input field. */
  complete: (loanRequestId: string, taskId: string, input: string) => Promise<void>;
  /** What the area 'information' shows: a component with the inputs form, texts and language. */
  information: Type<unknown>;
}

/** What one area tells another one of the same form. */
type AreaMessage = { input?: string; askForInput?: boolean; completed?: boolean };

type Problem = 'notFound' | 'claimed' | 'failed';

/**
 * What went wrong, from the status of the answer. 404: the task is not open any more. 409:
 * somebody else took the task over in the cockpit, and it is still open for them. Everything
 * else, a network error included, is a failure the user may try again.
 */
const PROBLEMS: Record<number, Problem> = { 404: 'notFound', 409: 'claimed' };

const problemOf = (error: unknown): Problem => (error instanceof ResponseError && PROBLEMS[error.response.status]) || 'failed';

/** One form: its definition, and the data of its user tasks, loaded once for all its areas. */
interface InputFormOfModule {
  definition: InputForm<unknown>;
  cache: Map<string, Promise<unknown>>;
}

const INPUT_FORM = new InjectionToken<InputFormOfModule>('InputForm');

/** The areas 'body' and 'information' both need the data. It is loaded once for both. */
function loadOnce({ definition, cache }: InputFormOfModule, loanRequestId: string, taskId: string) {
  let loading = cache.get(taskId);
  if (!loading) {
    loading = definition.load(loanRequestId, taskId);
    loading.catch(() => cache.delete(taskId));
    cache.set(taskId, loading);
  }
  return loading;
}

/** What every area has: the properties of the cockpit, the definition of the form, the texts. */
@Directive()
abstract class Area {
  readonly props = input.required<BcElementProps>();
  protected readonly inputForm = inject(INPUT_FORM);
  protected readonly definition = this.inputForm.definition;
  protected readonly testId = this.definition.testId;
  protected readonly userTask = computed(() => this.props().userTask as BcUserTask);
  /**
   * The cockpit hands over a new user task object whenever it sets any property, the language
   * for example. The ids stay the same then, so whatever depends on the ids only does not run again.
   */
  protected readonly userTaskId = computed(() => this.userTask().id!);
  /** The business id of the user task, which is the id of the loan request. */
  protected readonly loanRequestId = computed((): string => this.userTask().businessId);
  protected readonly texts = computed(() => textsFor(this.props().language));
  protected readonly language = computed(() => String(this.props().language ?? 'en'));
}

/** An area which shows the data of the form. It loads them when the user task changes. */
@Directive()
abstract class AreaWithData extends Area {
  protected readonly form = signal<unknown>(undefined);
  protected readonly loadProblem = signal<Problem | undefined>(undefined);

  constructor() {
    super();
    effect((onCleanup) => {
      const loanRequestId = this.loanRequestId();
      const userTaskId = this.userTaskId();
      let current = true;
      onCleanup(() => { current = false; });
      untracked(() => loadOnce(this.inputForm, loanRequestId, userTaskId).then(
        (loaded) => {
          if (!current) return;
          this.form.set(loaded);
          this.loaded(loaded);
        },
        (error) => current && this.loadProblem.set(problemOf(error)),
      ));
    });
  }

  /** Called once the data are there. */
  protected loaded(_form: unknown) {}
}

/** Completes the user task and tells the cockpit through a notification. */
class Completion {
  readonly state = signal<'open' | 'running' | 'completed'>('open');
  readonly problem = signal<Problem | undefined>(undefined);

  constructor(private readonly inputForm: InputFormOfModule, private readonly props: () => BcElementProps) {}

  async complete(input: string) {
    const { userTask, showNotification, language } = this.props();
    const texts = textsFor(language);
    this.state.set('running');
    this.problem.set(undefined);
    try {
      await this.inputForm.definition.complete(userTask!.businessId, userTask!.id!, input.trim());
      this.inputForm.cache.delete(userTask!.id!);
      this.state.set('completed');
      // A cockpit which cannot show notifications refuses; the form says it anyway.
      showNotification({ level: 'success', text: texts.completed }).catch(() => {});
      return true;
    } catch (error) {
      this.state.set('open');
      this.problem.set(problemOf(error));
      showNotification({ level: 'error', text: texts[problemOf(error)] }).catch(() => {});
      return false;
    }
  }
}

const PROBLEM = `
  @if (problem(); as problem) {
    <p class="problem" [attr.data-testid]="testId + '-error'">{{ texts()[problem] }}</p>
  }`;

const INFORMATION = `
  <ng-container *ngComponentOutlet="definition.information; inputs: { form: form(), texts: texts(), language: language() }" />`;

const INPUT_FIELD = `
  <div class="field" [attr.data-testid]="testId + '-form'">
    <label for="input">{{ definition.label(texts()) }}</label>
    <textarea id="input" [attr.data-testid]="testId + '-input'" [value]="input()" [disabled]="inputDisabled()"
      (input)="change($any($event.target).value)"></textarea>
  </div>`;

const COMPLETE_BUTTON = `
  <div class="actions">
    <button type="button" [attr.data-testid]="testId + '-complete'" [disabled]="completion.state() !== 'open' || input().trim() === ''"
      (click)="complete()">{{ texts().complete }}</button>
    @if (completion.state() === 'completed') {
      <span [attr.data-testid]="testId + '-done'">{{ texts().completed }}</span>
    }
  </div>`;

/** The area 'all': everything in one web component, so no messages are needed. */
@Component({
  selector: 'loan-approval-input-form-all',
  imports: [NgComponentOutlet],
  template: `
    ${PROBLEM}
    @if (form()) {
      ${INFORMATION}
      ${INPUT_FIELD}
      ${COMPLETE_BUTTON}
    } @else if (!loadProblem()) {
      <p>{{ texts().loading }}</p>
    }`,
})
class AllArea extends AreaWithData {
  protected readonly completion = new Completion(this.inputForm, this.props);
  protected readonly input = signal('');
  protected readonly problem = computed(() => this.loadProblem() ?? this.completion.problem());
  protected readonly inputDisabled = computed(() => this.completion.state() !== 'open');

  protected override loaded(form: unknown) {
    this.input.set(this.definition.initialInput(form));
  }

  protected change(value: string) {
    this.input.set(value);
  }

  protected complete() {
    void this.completion.complete(this.input());
  }
}

/** The area 'body': the input field. It sends every change to the area 'actions'. */
@Component({
  selector: 'loan-approval-input-form-body',
  template: `
    ${PROBLEM}
    @if (form()) {
      ${INPUT_FIELD}
    } @else if (!loadProblem()) {
      <p>{{ texts().loading }}</p>
    }`,
})
class BodyArea extends AreaWithData {
  protected readonly input = signal('');
  protected readonly completed = signal(false);
  protected readonly problem = this.loadProblem;
  protected readonly inputDisabled = this.completed;

  constructor() {
    super();
    effect((onCleanup) => {
      const userTask = this.userTask();
      const subscription = userTask.receiveMessage((message) => {
        const { askForInput, completed } = (message ?? {}) as AreaMessage;
        if (askForInput) this.tellActions(userTask, untracked(this.input));
        if (completed) this.completed.set(true);
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected override loaded(form: unknown) {
    this.change(this.definition.initialInput(form));
  }

  protected change(value: string) {
    this.input.set(value);
    this.tellActions(this.userTask(), value);
  }

  private tellActions(userTask: BcUserTask, input: string) {
    // No 'actions' area in this cockpit means nobody to tell.
    userTask.sendTo('actions', { input } satisfies AreaMessage).catch(() => {});
  }
}

/** The area 'information': the data to read. */
@Component({
  selector: 'loan-approval-input-form-information',
  imports: [NgComponentOutlet],
  template: `
    @if (form()) {
      ${INFORMATION}
    }`,
})
class InformationArea extends AreaWithData {}

/**
 * The area 'actions': the button. It gets the value of the input field from the area 'body'.
 * 'body' may have sent its first value before this area listened, so this area asks for it once
 * it listens.
 */
@Component({
  selector: 'loan-approval-input-form-actions',
  template: `
    ${PROBLEM}
    ${COMPLETE_BUTTON}`,
})
class ActionsArea extends Area {
  protected readonly completion = new Completion(this.inputForm, this.props);
  protected readonly input = signal('');
  protected readonly problem = this.completion.problem;

  constructor() {
    super();
    effect((onCleanup) => {
      const userTask = this.userTask();
      const subscription = userTask.receiveMessage((message) => {
        const { input } = (message ?? {}) as AreaMessage;
        if (typeof input === 'string') this.input.set(input);
      });
      subscription.ready.then(() => userTask.sendTo('body', { askForInput: true } satisfies AreaMessage)).catch(() => {});
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected async complete() {
    if (await this.completion.complete(this.input())) {
      this.userTask().sendTo('body', { completed: true } satisfies AreaMessage).catch(() => {});
    }
  }
}

export function defineInputForm<Form>(elements: { all: string; body: string; information: string; actions: string }, definition: InputForm<Form>) {
  const inputForm: InputFormOfModule = { definition: definition as InputForm<unknown>, cache: new Map() };
  const providers = [{ provide: INPUT_FORM, useValue: inputForm }];
  defineAngularElement(elements.all, AllArea, providers);
  defineAngularElement(elements.body, BodyArea, providers);
  defineAngularElement(elements.information, InformationArea, providers);
  defineAngularElement(elements.actions, ActionsArea, providers);
}
