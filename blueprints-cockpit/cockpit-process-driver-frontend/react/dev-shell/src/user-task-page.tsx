// The page of a user task: the dev shell reads the user task from the simulated cockpit and lets
// the host of the module kit draw its form, the same way a cockpit does. The form is the one this
// project builds, from dist/micro-frontends. Before it draws, the dev shell checks the names of
// the web components the way a cockpit does, see element-names.ts.

import { useEffect, useRef, useState } from 'react';
import { WORKFLOW_MODULE_ID } from '@loan-approval';
import { cockpit, host, notifications, onNotification, titleOf, waitFor, type CockpitUserTask } from './cockpit.ts';
import { refusedUserTaskElements } from './element-names.ts';

export function UserTaskPage({ userTaskId }: { userTaskId: string }) {
  const container = useRef<HTMLElement>(null);
  const [userTask, setUserTask] = useState<CockpitUserTask | null>();
  const [problem, setProblem] = useState<string>();
  const [, setNotificationCount] = useState(notifications.length);

  useEffect(() => onNotification(() => setNotificationCount(notifications.length)), []);

  useEffect(() => {
    let drawn: { remove(): void } | undefined;
    let current = true;
    (async () => {
      const found = await waitFor(() => cockpit.userTask(userTaskId), 3_000);
      if (!current) return;
      setUserTask(found ?? null);
      if (!found) return;
      // Like a cockpit: a name it would refuse is not loaded and not drawn, and the note says why.
      const refused = await refusedUserTaskElements(host, WORKFLOW_MODULE_ID, found);
      if (!current) return;
      if (refused) {
        console.error(refused);
        setProblem(refused);
        return;
      }
      const part = await host.showUserTask(WORKFLOW_MODULE_ID, found, container.current!);
      if (!current) part?.remove();
      else if (!part) setProblem(`The module has no form for the user task '${found.taskDefinition}'.`);
      else drawn = part;
    })().catch((error) => current && setProblem(String(error.message ?? error)));
    return () => {
      current = false;
      drawn?.remove();
    };
  }, [userTaskId]);

  return (
    <>
      {userTask === null ? <p className="problem" data-testid="user-task-error">The simulated cockpit does not know the user task {userTaskId}.</p> : null}
      {userTask ? (
        <>
          <h1 data-testid="user-task-title">{titleOf(userTask, userTask.id)}</h1>
          {userTask.workflowId ? <p><a href={`#/workflow/${encodeURIComponent(userTask.workflowId)}`}>Back to the case</a></p> : null}
          {userTask.endedAt ? <p className="hint">This user task is completed.</p> : null}
        </>
      ) : null}
      {problem ? <p className="problem" data-testid="user-task-error">{problem}</p> : null}
      <section ref={container} data-testid="user-task-form">
        <div data-bc-area="body"></div>
        <div data-bc-area="actions"></div>
      </section>
      <ul data-testid="notifications">
        {notifications.map((notification, index) => <li key={index}>{notification.level}: {notification.text}</li>)}
      </ul>
    </>
  );
}
