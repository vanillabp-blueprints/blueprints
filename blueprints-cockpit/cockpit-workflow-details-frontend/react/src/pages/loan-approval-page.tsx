// Visual entry LoanApprovalPage: the page of a loan approval case. It exports nothing. When the
// browser evaluates it, it defines the four web components the data entry WorkflowPage names:
//
//   all          the whole page in one web component, for a cockpit without areas
//   body         the open user tasks of the case, which the cockpit lists
//   information  the data of the loan request, which the workflow module gives
//   actions      the button "Cancel case"
//
// In a cockpit with areas, the three areas are three web components, and nothing is shared
// between them but DOM events and the data this file loads once for all of them. When "Cancel
// case" succeeds, 'actions' tells 'body' and 'information' with sendTo(), and they load their
// data again. The cockpit also sets the property 'workflow' again when it learns that the case
// changed, and that loads the user tasks again as well.

import { useEffect, useState } from 'react';
import type { BcElementProps, BcUserTask, BcWorkflow } from '@vanillabp/bc-module-kit';
import { ApiError, loanApprovalApi, type Case } from '../shared/api.ts';
import { defineReactElement } from '../shared/react-element.tsx';
import { STYLE } from '../shared/style.ts';
import { textsFor, type Texts } from '../shared/texts.ts';
import { LOAN_APPROVAL_PAGE_ELEMENTS } from '../loan-approval.ts';

/** What one area tells another one of the same page. */
type AreaMessage = { cancelled?: boolean };

type Problem = 'notFound' | 'hasEnded' | 'failed';

const problemOf = (error: unknown): Problem => {
  if (error instanceof ApiError && error.status === 404) return 'notFound';
  if (error instanceof ApiError && error.status === 409) return 'hasEnded';
  return 'failed';
};

/** The case knows its loan request by the business id, which is the id of the loan request. */
const loanRequestIdOf = (workflow: BcWorkflow) => String(workflow.businessId);

/** The areas 'information' and 'actions' both need the case. It is loaded once for both. */
const cases = new Map<string, Promise<Case>>();

function loadCase(loanRequestId: string) {
  let loading = cases.get(loanRequestId);
  if (!loading) {
    loading = loanApprovalApi.getCase(loanRequestId);
    loading.catch(() => cases.delete(loanRequestId));
    cases.set(loanRequestId, loading);
  }
  return loading;
}

function useCase(loanRequestId: string, reload: number) {
  const [loaded, setLoaded] = useState<Case>();
  const [problem, setProblem] = useState<Problem>();
  useEffect(() => {
    let current = true;
    loadCase(loanRequestId).then(
      (found) => {
        if (!current) return;
        setLoaded(found);
        setProblem(undefined);
      },
      (error) => current && setProblem(problemOf(error)),
    );
    return () => { current = false; };
  }, [loanRequestId, reload]);
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
function useOpenUserTasks(workflow: BcWorkflow, reload: number) {
  const [userTasks, setUserTasks] = useState<BcUserTask[]>();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let current = true;
    workflow.getUserTasks(true, false).then(
      (found) => {
        if (!current) return;
        setUserTasks(found);
        setFailed(false);
      },
      () => current && setFailed(true),
    );
    return () => { current = false; };
    // The cockpit sets 'workflow' again whenever the case changes, as a new object. So the
    // effect depends on what tells such a change, not on the object.
  }, [workflow.id, workflow.updatedAt, workflow.endedAt, reload]);
  return { userTasks, failed };
}

/** Counts up whenever another area says that the case was cancelled. */
function useReloadOnMessage(workflow: BcWorkflow) {
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const subscription = workflow.receiveMessage((message) => {
      if ((message as AreaMessage | undefined)?.cancelled) setReload((count) => count + 1);
    });
    return () => { subscription.unsubscribe(); };
    // The subscription belongs to the web component, which stays the same while the cockpit
    // sets 'workflow' again. So it is made once per case.
  }, [workflow.id]);
  return reload;
}

const ProblemText = ({ problem, texts, testId }: { problem?: Problem; texts: Texts; testId: string }) =>
  problem ? <p className="problem" data-testid={testId}>{texts[problem]}</p> : null;

function titleOf(userTask: BcUserTask, language: string) {
  const title = userTask.title as Record<string, string> | string | undefined;
  if (typeof title === 'string') return title;
  return title?.[language] ?? title?.en ?? Object.values(title ?? {})[0] ?? String(userTask.taskDefinition ?? userTask.id);
}

function UserTasks({ workflow, texts, language, reload }: { workflow: BcWorkflow; texts: Texts; language: string; reload: number }) {
  const { userTasks, failed } = useOpenUserTasks(workflow, reload);
  return (
    <section data-testid="case-user-tasks">
      <h2>{texts.openUserTasks}</h2>
      {failed ? <p className="problem" data-testid="case-user-tasks-error">{texts.userTasksFailed}</p> : null}
      {userTasks === undefined ? (failed ? null : <p>{texts.loading}</p>) : userTasks.length === 0 ? (
        <p data-testid="case-no-user-tasks">{texts.noOpenUserTasks}</p>
      ) : (
        <ul>
          {userTasks.map((userTask) => (
            <li key={userTask.id} data-testid="case-user-task">{titleOf(userTask, language)}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Information({ loaded, texts, language }: { loaded: Case; texts: Texts; language: string }) {
  return (
    <dl>
      <dt>{texts.loanRequest}</dt>
      <dd data-testid="case-loan-request">{loaded.loanRequestId}</dd>
      <dt>{texts.applicant}</dt>
      <dd data-testid="case-applicant">{loaded.applicant}</dd>
      <dt>{texts.amount}</dt>
      <dd data-testid="case-amount">{loaded.amount.toLocaleString(language)}</dd>
      <dt>{texts.outcome}</dt>
      <dd data-testid="case-outcome">{loaded.outcome ? texts[loaded.outcome] : texts.running}</dd>
    </dl>
  );
}

/**
 * The button "Cancel case". It calls the module, and the module correlates the message which
 * starts the event subprocess. Only a running case has the button enabled.
 */
function CancelButton({ loaded, texts, showNotification, onCancelled }: {
  loaded?: Case;
  texts: Texts;
  showNotification: BcElementProps['showNotification'];
  onCancelled: () => void;
}) {
  const [state, setState] = useState<'open' | 'running' | 'cancelled'>('open');
  const [problem, setProblem] = useState<Problem>();
  const cancel = async () => {
    setState('running');
    setProblem(undefined);
    try {
      await loanApprovalApi.cancelCase(loaded!.loanRequestId);
      cases.delete(loaded!.loanRequestId);
      setState('cancelled');
      // A cockpit which cannot show notifications refuses; the page says it anyway.
      showNotification({ level: 'success', text: texts.cancelled }).catch(() => {});
      onCancelled();
    } catch (error) {
      setState('open');
      setProblem(problemOf(error));
      showNotification({ level: 'error', text: texts[problemOf(error)] }).catch(() => {});
    }
  };
  return (
    <div className="actions">
      <ProblemText problem={problem} texts={texts} testId="case-cancel-error" />
      <button type="button" data-testid="case-cancel" disabled={!loaded || loaded.outcome !== undefined || state !== 'open'} onClick={cancel}>
        {texts.cancel}
      </button>
      {state === 'cancelled' ? <span data-testid="case-cancelled">{texts.cancelled}</span> : null}
    </div>
  );
}

/** The area 'all': everything in one web component, so no messages are needed. */
function All(props: BcElementProps) {
  const workflow = props.workflow as BcWorkflow;
  const texts = textsFor(props.language);
  const language = String(props.language ?? 'en');
  const [reload, setReload] = useState(0);
  const { loaded, problem } = useCase(loanRequestIdOf(workflow), reload);
  return (
    <>
      <style>{STYLE}</style>
      <ProblemText problem={problem} texts={texts} testId="case-error" />
      {loaded ? <Information loaded={loaded} texts={texts} language={language} /> : problem ? null : <p>{texts.loading}</p>}
      <UserTasks workflow={workflow} texts={texts} language={language} reload={reload} />
      <CancelButton loaded={loaded} texts={texts} showNotification={props.showNotification} onCancelled={() => setReload((count) => count + 1)} />
    </>
  );
}

/** The area 'body': the open user tasks. */
function Body(props: BcElementProps) {
  const workflow = props.workflow as BcWorkflow;
  const reload = useReloadOnMessage(workflow);
  return (
    <>
      <style>{STYLE}</style>
      <UserTasks workflow={workflow} texts={textsFor(props.language)} language={String(props.language ?? 'en')} reload={reload} />
    </>
  );
}

/** The area 'information': the data of the loan request. */
function InformationArea(props: BcElementProps) {
  const workflow = props.workflow as BcWorkflow;
  const texts = textsFor(props.language);
  const reload = useReloadOnMessage(workflow);
  const { loaded, problem } = useCase(loanRequestIdOf(workflow), reload);
  return (
    <>
      <style>{STYLE}</style>
      <ProblemText problem={problem} texts={texts} testId="case-error" />
      {loaded ? <Information loaded={loaded} texts={texts} language={String(props.language ?? 'en')} /> : problem ? null : <p>{texts.loading}</p>}
    </>
  );
}

/** The area 'actions': the button. It tells the other two areas when the case is cancelled. */
function Actions(props: BcElementProps) {
  const workflow = props.workflow as BcWorkflow;
  const texts = textsFor(props.language);
  const [reload, setReload] = useState(0);
  const { loaded } = useCase(loanRequestIdOf(workflow), reload);
  const cancelled = () => {
    setReload((count) => count + 1);
    // No such area in this cockpit means nobody to tell.
    for (const area of ['body', 'information']) workflow.sendTo(area, { cancelled: true } satisfies AreaMessage).catch(() => {});
  };
  return (
    <>
      <style>{STYLE}</style>
      <CancelButton loaded={loaded} texts={texts} showNotification={props.showNotification} onCancelled={cancelled} />
    </>
  );
}

defineReactElement(LOAN_APPROVAL_PAGE_ELEMENTS.all, All);
defineReactElement(LOAN_APPROVAL_PAGE_ELEMENTS.body, Body);
defineReactElement(LOAN_APPROVAL_PAGE_ELEMENTS.information, InformationArea);
defineReactElement(LOAN_APPROVAL_PAGE_ELEMENTS.actions, Actions);
