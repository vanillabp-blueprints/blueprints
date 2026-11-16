// The dev shell: a page in the browser which plays the cockpit for this module during
// development. It has three pages, chosen by the part of the address after '#':
//
//   #/process-driver        brings a new case to a state, the start page
//   #/workflow/<id>         a case and its user tasks
//   #/user-task/<id>        the form of a user task, drawn by the host of the module kit
//
// `npm run start` serves it and forwards the calls to the application and to the simulated
// cockpit, see scripts/start.mjs. So the page never needs to know their ports.

import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ProcessDriverPage } from './process-driver-page.tsx';
import { UserTaskPage } from './user-task-page.tsx';
import { WorkflowPage } from './workflow-page.tsx';

function useHash() {
  const [hash, setHash] = useState(location.hash);
  useEffect(() => {
    const changed = () => setHash(location.hash);
    addEventListener('hashchange', changed);
    return () => removeEventListener('hashchange', changed);
  }, []);
  return hash;
}

function DevShell() {
  const [, page, id] = useHash().match(/^#\/([^/]+)\/?(.*)$/) ?? [];
  if (page === 'workflow' && id) return <WorkflowPage key={id} workflowId={decodeURIComponent(id)} />;
  if (page === 'user-task' && id) return <UserTaskPage key={id} userTaskId={decodeURIComponent(id)} />;
  return <ProcessDriverPage />;
}

createRoot(document.getElementById('dev-shell')!).render(<DevShell />);
