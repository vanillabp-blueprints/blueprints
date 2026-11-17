// The page "Process driver". It brings a new loan approval to a state of its process and links to
// what the dev shell shows of it. Somebody who works on a form gets a case in the state they need
// in a few seconds, without clicking through the process.
//
// 1. When the page opens, it asks the backend for the states. A new or renamed state of the
//    driver shows up here without a change of the dev shell.
// 2. The form sends the state and the test data to advance-to. The backend brings a real loan
//    request there, through the business code and the simulator, and answers with the id of the
//    loan request and of the open user task. A state where the workflow ended, like a contract
//    the customer declined, has no open user task.
// 3. The page waits until the simulated cockpit knows the workflow and the user task, if there is
//    one, and links to both pages of the dev shell.

import { useEffect, useState, type FormEvent } from 'react';
import { processDriver, ResponseError, type Reached } from '@process-driver-client';
import { cockpit, CockpitError, titleOf, waitFor, type CockpitUserTask, type CockpitWorkflow } from './cockpit.ts';

/** What switches the driver on. The page says it whenever the driver does not answer. */
export const DRIVER_SWITCH = "Start the application with the Maven profile 'test-driver' and the property"
  + " 'process-driver.enabled=true'. The README of the backend shows the command.";

/**
 * The test data the form starts with. The amount is above the limit of the risk assessment,
 * 10000 by default, so every state can be reached with it. A smaller amount skips "Assess risk".
 */
const DEFAULT_AMOUNT = 25000;
const DEFAULT_CREDIT_SCORE = 720;

/** Why the driver gave no case, in words which say what to do. */
function problemOf(error: unknown): string {
  if (error instanceof CockpitError) return error.message;
  if (!(error instanceof ResponseError)) return `The application does not answer. ${DRIVER_SWITCH}`;
  const { status } = error.response;
  if (status === 404) return `The process driver is not switched on (404). ${DRIVER_SWITCH}`;
  // The dev shell answers 502 itself when nothing listens at the address of the application.
  if (status === 502) return `The application does not answer. ${DRIVER_SWITCH}`;
  // 400 for an unknown state, 500 when the case did not reach the state. The answer says why.
  return `The process driver answered ${status}. ${typeof error.body === 'string' ? error.body : ''}`.trim();
}

interface Result {
  reached: Reached;
  workflow?: CockpitWorkflow;
  userTask?: CockpitUserTask;
}

export function ProcessDriverPage() {
  const [states, setStates] = useState<string[]>([]);
  const [state, setState] = useState('');
  const [amount, setAmount] = useState(String(DEFAULT_AMOUNT));
  const [creditScore, setCreditScore] = useState(String(DEFAULT_CREDIT_SCORE));
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<Result>();
  const [problem, setProblem] = useState<string>();

  useEffect(() => {
    processDriver.states().then(
      (known) => {
        setStates(known);
        setState(known[0] ?? '');
      },
      (error) => setProblem(problemOf(error)),
    );
  }, []);

  const advance = async (event: FormEvent) => {
    event.preventDefault();
    setRunning(true);
    setResult(undefined);
    setProblem(undefined);
    try {
      const reached = await processDriver.advanceTo({ state, amount: Number(amount), creditScore: Number(creditScore) });
      const workflow = await waitFor(() => cockpit.workflowOf(reached.loanRequestId));
      const { userTaskId } = reached;
      const userTask = userTaskId ? await waitFor(() => cockpit.userTask(userTaskId)) : undefined;
      setResult({ reached, workflow, userTask });
    } catch (error) {
      setProblem(problemOf(error));
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <h1>Process driver</h1>
      <p className="hint">Brings a new loan request to the state you choose, with the real business code and the simulator.</p>
      <form onSubmit={advance}>
        <label>
          State
          <select data-testid="process-driver-state" value={state} disabled={states.length === 0} onChange={(event) => setState(event.target.value)}>
            {states.map((known) => <option key={known} value={known}>{known}</option>)}
          </select>
        </label>
        <label>
          Amount
          <input data-testid="process-driver-amount" type="number" min="1" required value={amount} onChange={(event) => setAmount(event.target.value)} />
        </label>
        <label>
          Credit score
          <input data-testid="process-driver-credit-score" type="number" min="0" required value={creditScore} onChange={(event) => setCreditScore(event.target.value)} />
        </label>
        <button type="submit" data-testid="process-driver-advance" disabled={running || state === ''}>
          {running ? 'Running…' : 'Create the case'}
        </button>
      </form>
      {problem ? <p className="problem" data-testid="process-driver-error">{problem}</p> : null}
      {result ? <ReachedCase result={result} /> : null}
    </>
  );
}

function ReachedCase({ result: { reached, workflow, userTask } }: { result: Result }) {
  return (
    <section data-testid="process-driver-result">
      <p>
        Loan request <code data-testid="process-driver-loan-request">{reached.loanRequestId}</code> reached{' '}
        <code>{reached.state}</code>.
      </p>
      <ul>
        {userTask ? (
          <li><a data-testid="process-driver-open-user-task" href={`#/user-task/${encodeURIComponent(userTask.id)}`}>Open the user task "{titleOf(userTask, userTask.id)}"</a></li>
        ) : null}
        {workflow ? (
          <li><a data-testid="process-driver-open-workflow" href={`#/workflow/${encodeURIComponent(workflow.id)}`}>Open the case "{titleOf(workflow, workflow.id)}"</a></li>
        ) : null}
      </ul>
      {reached.userTaskId ? null : (
        <p className="hint" data-testid="process-driver-no-user-task">The workflow ended in this state, so no user task is open.</p>
      )}
      {workflow && (userTask || !reached.userTaskId) ? null : (
        <p className="problem" data-testid="process-driver-not-in-cockpit">
          The simulated cockpit does not know the case yet. Is the simulator of the backend running, and does the application report to it?
        </p>
      )}
    </section>
  );
}
