// The page of a case: what the simulated cockpit knows of the workflow, and its user tasks with a
// link to each. This blueprint has no page of its own for a case, so the dev shell lists the user
// tasks itself.

import { useEffect, useState } from 'react';
import { cockpit, titleOf, type CockpitUserTask, type CockpitWorkflow } from './cockpit.ts';

export function WorkflowPage({ workflowId }: { workflowId: string }) {
  const [workflow, setWorkflow] = useState<CockpitWorkflow | null>();
  const [userTasks, setUserTasks] = useState<CockpitUserTask[]>([]);
  const [problem, setProblem] = useState<string>();

  useEffect(() => {
    Promise.all([cockpit.workflow(workflowId), cockpit.userTasksOf(workflowId)]).then(
      ([found, tasks]) => {
        setWorkflow(found ?? null);
        setUserTasks(tasks ?? []);
      },
      (error) => setProblem(String(error.message ?? error)),
    );
  }, [workflowId]);

  if (problem) return <p className="problem" data-testid="workflow-error">{problem}</p>;
  if (workflow === undefined) return <p>Loading…</p>;
  if (workflow === null) return <p className="problem" data-testid="workflow-error">The simulated cockpit does not know the case {workflowId}.</p>;
  return (
    <>
      <h1 data-testid="workflow-title">{titleOf(workflow, workflow.id)}</h1>
      <p>Loan request <code data-testid="workflow-business-id">{workflow.businessId}</code>{workflow.endedAt ? ', completed' : ''}</p>
      <h2>User tasks</h2>
      <ul data-testid="workflow-user-tasks">
        {userTasks.map((userTask) => (
          <li key={userTask.id}>
            <a data-testid="workflow-user-task" href={`#/user-task/${encodeURIComponent(userTask.id)}`}>{titleOf(userTask, userTask.id)}</a>
            {userTask.endedAt ? ' (completed)' : ''}
          </li>
        ))}
      </ul>
    </>
  );
}
