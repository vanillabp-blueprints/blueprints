// Visual entry LoanApprovalProcessPage: the page of a loan approval case, with its process. It
// exports nothing. When the browser evaluates it, it defines the one web component the data entry
// WorkflowPage names, for the area 'all'.
//
// The page loads the BPMN XML and the history from the module API. At the same time it imports
// src/pages/draw-process.ts with import(). That file brings bpmn-js, so bpmn-js is a chunk of its
// own, and the browser loads it only here. The cockpit sets the property 'workflow' again when the
// case changes, and the page then loads the history again and draws the markers anew.

import { useEffect, useRef, useState } from 'react';
import type { BcElementProps, BcWorkflow } from '@vanillabp/bc-module-kit';
import { defineReactElement, loadProcess, loanRequestIdOf, problemOf, STYLE, textsFor, type Problem } from '@shared';
import { LOAN_APPROVAL_PROCESS_ELEMENT } from '@loan-approval';

function ProcessPage(props: BcElementProps) {
  const workflow = props.workflow as BcWorkflow;
  const texts = textsFor(props.language);
  const diagram = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<'loading' | 'drawn' | Problem>('loading');
  const loanRequestId = loanRequestIdOf(workflow);

  useEffect(() => {
    let current = true;
    let removeDiagram: (() => void) | undefined;
    Promise.all([loadProcess(loanRequestId), import('./draw-process.ts')])
      .then(async ([{ bpmnXml, history }, { drawProcess }]) => {
        if (!current || !diagram.current) return;
        const remove = await drawProcess(diagram.current, bpmnXml, history);
        if (current) {
          removeDiagram = remove;
          setState('drawn');
        } else {
          remove();
        }
      })
      .catch((error) => current && setState(problemOf(error)));
    return () => {
      current = false;
      removeDiagram?.();
    };
    // The cockpit sets 'workflow' again whenever the case changes, as a new object. So the
    // effect depends on what tells such a change, not on the object.
  }, [loanRequestId, workflow.updatedAt, workflow.endedAt]);

  const problem = state === 'notFound' || state === 'failed' ? state : undefined;
  return (
    <>
      <style>{STYLE}</style>
      <section data-testid="process-view">
        <h2>{texts.process}</h2>
        {problem ? <p className="problem" data-testid="process-error">{texts[problem]}</p> : null}
        {state === 'loading' ? <p data-testid="process-loading">{texts.loading}</p> : null}
        <div className="diagram" data-testid="process-diagram" hidden={problem !== undefined} ref={diagram} />
        {state === 'drawn'
          ? (
            <ul className="legend">
              <li className="passed">{texts.passed}</li>
              <li className="current">{texts.current}</li>
            </ul>
          )
          : null}
      </section>
    </>
  );
}

defineReactElement(LOAN_APPROVAL_PROCESS_ELEMENT, ProcessPage);
