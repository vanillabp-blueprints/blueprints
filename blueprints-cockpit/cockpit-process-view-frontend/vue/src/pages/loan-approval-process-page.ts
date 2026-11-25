// Visual entry LoanApprovalProcessPage: the page of a loan approval case, with its process. It
// exports nothing. When the browser evaluates it, it defines the one web component the data entry
// WorkflowPage names, for the area 'all'.
//
// The entry imports its own component and nothing else of the module's user interface. So the
// cockpit loads this page only when it shows a case. The component loads bpmn-js only when it
// draws the process, see src/pages/draw-process.ts.

import { defineVueElement } from '@shared';
import { LOAN_APPROVAL_PROCESS_ELEMENT } from '@loan-approval';
import ProcessView from './ProcessView.ce.vue';

defineVueElement(LOAN_APPROVAL_PROCESS_ELEMENT, ProcessView);
