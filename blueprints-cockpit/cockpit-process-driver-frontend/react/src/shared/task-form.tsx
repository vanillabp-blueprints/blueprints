// Both user tasks of this module have a form of the same kind: the data of the loan request to
// read and one button which completes the task. The module API takes no input for them. This
// file builds the three web components of such a form, so each form file only says what is
// different.
//
//   all      the whole form in one web component, for a cockpit without areas
//   body     the data of the loan request
//   actions  the button
//
// The data comes with the user task. The backend puts the amount and the credit score into its
// details, and the business id of the user task is the id of the loan request. So the form calls
// the module API only to complete the task.

import { useState } from 'react';
import type { BcElementProps, BcUserTask } from '@vanillabp/bc-module-kit';
import { ResponseError } from './response-error.ts';
import { AMOUNT, CREDIT_SCORE } from '@loan-approval';
import { defineReactElement } from './react-element.tsx';
import { STYLE } from './style.ts';
import { textsFor, type Texts } from './texts.ts';

export interface TaskForm {
  /** The start of every data-testid of the form, for example 'assess-risk'. */
  testId: string;
  /** The text of the button which completes the task. */
  completeLabel: (texts: Texts) => string;
  /** Completes the user task through the module API. */
  complete: (loanRequestId: string, taskId: string) => Promise<unknown>;
}

/** 409 is the answer of "Check contract" while the signature is missing. The task stays open. */
type Problem = 'notSigned' | 'failed';

const problemOf = (error: unknown): Problem => (error instanceof ResponseError && error.response.status === 409 ? 'notSigned' : 'failed');

export function defineTaskForm(elements: { all: string; body: string; actions: string }, definition: TaskForm) {
  const { testId } = definition;

  function LoanRequest({ userTask, texts, language }: { userTask: BcUserTask; texts: Texts; language: string }) {
    const details = userTask.details ?? {};
    return (
      <dl>
        <dt>{texts.loanRequest}</dt>
        <dd data-testid={`${testId}-loan-request`}>{userTask.businessId}</dd>
        <dt>{texts.amount}</dt>
        <dd data-testid={`${testId}-amount`}>{Number(details[AMOUNT]).toLocaleString(language)}</dd>
        <dt>{texts.creditScore}</dt>
        <dd data-testid={`${testId}-credit-score`}>{details[CREDIT_SCORE]}</dd>
      </dl>
    );
  }

  /** The button. It completes the user task and tells the cockpit through a notification. */
  function CompleteButton({ userTask, showNotification, language }: BcElementProps) {
    const texts = textsFor(language);
    const [state, setState] = useState<'open' | 'running' | 'completed'>('open');
    const [problem, setProblem] = useState<Problem>();
    const complete = async () => {
      setState('running');
      setProblem(undefined);
      try {
        await definition.complete(userTask!.businessId, userTask!.id!);
        setState('completed');
        // A cockpit which cannot show notifications refuses; the form says it anyway.
        showNotification({ level: 'success', text: texts.completed }).catch(() => {});
      } catch (error) {
        setState('open');
        setProblem(problemOf(error));
        showNotification({ level: 'error', text: texts[problemOf(error)] }).catch(() => {});
      }
    };
    return (
      <>
        {problem ? <p className="problem" data-testid={`${testId}-error`}>{texts[problem]}</p> : null}
        <div className="actions">
          <button type="button" data-testid={`${testId}-complete`} disabled={state !== 'open'} onClick={complete}>
            {definition.completeLabel(texts)}
          </button>
          {state === 'completed' ? <span data-testid={`${testId}-done`}>{texts.completed}</span> : null}
        </div>
      </>
    );
  }

  /** The area 'all': everything in one web component. */
  function All(props: BcElementProps) {
    return (
      <>
        <style>{STYLE}</style>
        <LoanRequest userTask={props.userTask!} texts={textsFor(props.language)} language={String(props.language ?? 'en')} />
        <CompleteButton {...props} />
      </>
    );
  }

  /** The area 'body': the data of the loan request. */
  function Body(props: BcElementProps) {
    return (
      <>
        <style>{STYLE}</style>
        <LoanRequest userTask={props.userTask!} texts={textsFor(props.language)} language={String(props.language ?? 'en')} />
      </>
    );
  }

  /** The area 'actions': the button. */
  function Actions(props: BcElementProps) {
    return (
      <>
        <style>{STYLE}</style>
        <CompleteButton {...props} />
      </>
    );
  }

  defineReactElement(elements.all, All);
  defineReactElement(elements.body, Body);
  defineReactElement(elements.actions, Actions);
}
