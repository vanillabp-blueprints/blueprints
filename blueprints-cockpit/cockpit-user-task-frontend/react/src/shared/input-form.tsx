// Both user tasks of this module have a form of the same kind: some data to read, one input field
// and a button "Complete". This file builds the four web components of such a form, so each form
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

import { useEffect, useRef, useState, type ComponentType } from 'react';
import type { BcElementProps, BcUserTask } from '@vanillabp/bc-module-kit';
import { ResponseError } from './api.ts';
import { defineReactElement } from './react-element.tsx';
import { STYLE } from './style.ts';
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
  /** What the area 'information' shows. */
  Information: ComponentType<{ form: Form; texts: Texts; language: string }>;
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

/** The areas 'body' and 'information' both need the data. It is loaded once for both. */
function loadOnce<Form>(definition: InputForm<Form>, cache: Map<string, Promise<Form>>, loanRequestId: string, taskId: string) {
  let loading = cache.get(taskId);
  if (!loading) {
    loading = definition.load(loanRequestId, taskId);
    loading.catch(() => cache.delete(taskId));
    cache.set(taskId, loading);
  }
  return loading;
}

export function defineInputForm<Form>(elements: { all: string; body: string; information: string; actions: string }, definition: InputForm<Form>) {
  const { testId, Information } = definition;
  const cache = new Map<string, Promise<Form>>();

  function useForm(userTask: BcUserTask) {
    const loanRequestId: string = userTask.businessId;
    const taskId = userTask.id!;
    const [form, setForm] = useState<Form>();
    const [problem, setProblem] = useState<Problem>();
    useEffect(() => {
      let current = true;
      loadOnce(definition, cache, loanRequestId, taskId).then(
        (loaded) => current && setForm(loaded),
        (error) => current && setProblem(problemOf(error)),
      );
      return () => { current = false; };
    }, [loanRequestId, taskId]);
    return { form, problem };
  }

  /** Completes the user task and tells the cockpit through a notification. */
  function useCompletion({ userTask, showNotification, language }: BcElementProps) {
    const texts = textsFor(language);
    const [state, setState] = useState<'open' | 'running' | 'completed'>('open');
    const [problem, setProblem] = useState<Problem>();
    const complete = async (input: string) => {
      setState('running');
      setProblem(undefined);
      try {
        await definition.complete(userTask!.businessId, userTask!.id!, input.trim());
        cache.delete(userTask!.id!);
        setState('completed');
        // A cockpit which cannot show notifications refuses; the form says it anyway.
        showNotification({ level: 'success', text: texts.completed }).catch(() => {});
        return true;
      } catch (error) {
        setState('open');
        setProblem(problemOf(error));
        showNotification({ level: 'error', text: texts[problemOf(error)] }).catch(() => {});
        return false;
      }
    };
    return { state, problem, complete };
  }

  const ProblemText = ({ problem, texts }: { problem?: Problem; texts: Texts }) =>
    problem ? <p className="problem" data-testid={`${testId}-error`}>{texts[problem]}</p> : null;

  function InputField({ texts, value, onChange, disabled }: { texts: Texts; value: string; onChange: (value: string) => void; disabled: boolean }) {
    return (
      <div className="field" data-testid={`${testId}-form`}>
        <label htmlFor="input">{definition.label(texts)}</label>
        <textarea id="input" data-testid={`${testId}-input`} value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} />
      </div>
    );
  }

  function CompleteButton({ texts, input, state, onComplete }: { texts: Texts; input: string; state: string; onComplete: () => void }) {
    return (
      <div className="actions">
        <button type="button" data-testid={`${testId}-complete`} disabled={state !== 'open' || input.trim() === ''} onClick={onComplete}>
          {texts.complete}
        </button>
        {state === 'completed' ? <span data-testid={`${testId}-done`}>{texts.completed}</span> : null}
      </div>
    );
  }

  /** The area 'all': everything in one web component, so no messages are needed. */
  function All(props: BcElementProps) {
    const texts = textsFor(props.language);
    const { form, problem } = useForm(props.userTask!);
    const completion = useCompletion(props);
    const [input, setInput] = useState('');
    useEffect(() => { if (form) setInput(definition.initialInput(form)); }, [form]);
    return (
      <>
        <style>{STYLE}</style>
        <ProblemText problem={problem ?? completion.problem} texts={texts} />
        {form ? (
          <>
            <Information form={form} texts={texts} language={String(props.language ?? 'en')} />
            <InputField texts={texts} value={input} onChange={setInput} disabled={completion.state !== 'open'} />
            <CompleteButton texts={texts} input={input} state={completion.state} onComplete={() => completion.complete(input)} />
          </>
        ) : problem ? null : <p>{texts.loading}</p>}
      </>
    );
  }

  /** The area 'body': the input field. It sends every change to the area 'actions'. */
  function Body(props: BcElementProps) {
    const userTask = props.userTask as BcUserTask;
    const texts = textsFor(props.language);
    const { form, problem } = useForm(userTask);
    const [input, setInput] = useState('');
    const [completed, setCompleted] = useState(false);
    const inputNow = useRef('');
    const change = (value: string) => {
      inputNow.current = value;
      setInput(value);
      // No 'actions' area in this cockpit means nobody to tell.
      userTask.sendTo('actions', { input: value } satisfies AreaMessage).catch(() => {});
    };
    useEffect(() => { if (form) change(definition.initialInput(form)); }, [form]);
    useEffect(() => {
      const subscription = userTask.receiveMessage((message) => {
        const { askForInput, completed: done } = (message ?? {}) as AreaMessage;
        if (askForInput) userTask.sendTo('actions', { input: inputNow.current } satisfies AreaMessage).catch(() => {});
        if (done) setCompleted(true);
      });
      return () => { subscription.unsubscribe(); };
    }, [userTask]);
    return (
      <>
        <style>{STYLE}</style>
        <ProblemText problem={problem} texts={texts} />
        {form ? <InputField texts={texts} value={input} onChange={change} disabled={completed} /> : problem ? null : <p>{texts.loading}</p>}
      </>
    );
  }

  /** The area 'information': the data to read. */
  function InformationArea(props: BcElementProps) {
    const texts = textsFor(props.language);
    const { form } = useForm(props.userTask!);
    return (
      <>
        <style>{STYLE}</style>
        {form ? <Information form={form} texts={texts} language={String(props.language ?? 'en')} /> : null}
      </>
    );
  }

  /**
   * The area 'actions': the button. It gets the value of the input field from the area 'body'.
   * 'body' may have sent its first value before this area listened, so this area asks for it
   * once it listens.
   */
  function Actions(props: BcElementProps) {
    const userTask = props.userTask as BcUserTask;
    const texts = textsFor(props.language);
    const completion = useCompletion(props);
    const [input, setInput] = useState('');
    useEffect(() => {
      const subscription = userTask.receiveMessage((message) => {
        const { input: value } = (message ?? {}) as AreaMessage;
        if (typeof value === 'string') setInput(value);
      });
      subscription.ready.then(() => userTask.sendTo('body', { askForInput: true } satisfies AreaMessage)).catch(() => {});
      return () => { subscription.unsubscribe(); };
    }, [userTask]);
    const complete = async () => {
      if (await completion.complete(input)) userTask.sendTo('body', { completed: true } satisfies AreaMessage).catch(() => {});
    };
    return (
      <>
        <style>{STYLE}</style>
        <ProblemText problem={completion.problem} texts={texts} />
        <CompleteButton texts={texts} input={input} state={completion.state} onComplete={complete} />
      </>
    );
  }

  defineReactElement(elements.all, All);
  defineReactElement(elements.body, Body);
  defineReactElement(elements.information, InformationArea);
  defineReactElement(elements.actions, Actions);
}
