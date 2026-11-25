// Visual entry LoanApprovalPage: the page of a loan approval case. It exports nothing. When the
// browser evaluates it, it defines the four web components the data entry WorkflowPage names:
//
//   all          the whole page in one web component, for a cockpit without areas
//   body         the open user tasks of the case, which the cockpit lists
//   information  the data of the loan request, which the workflow module gives
//   actions      the button "Withdraw loan request"
//
// The entry imports its own components and nothing else of the module's user interface. So the
// cockpit loads this page only when it shows a case. A dev shell which draws every part of the
// module keeps a list of names and components of its own, which no visual entry imports.

import { defineVueElement } from '@shared';
import { LOAN_APPROVAL_PAGE_ELEMENTS } from '@loan-approval';
import ActionsArea from './loan-approval-page/ActionsArea.ce.vue';
import AllArea from './loan-approval-page/AllArea.ce.vue';
import BodyArea from './loan-approval-page/BodyArea.ce.vue';
import InformationArea from './loan-approval-page/InformationArea.ce.vue';

defineVueElement(LOAN_APPROVAL_PAGE_ELEMENTS.all, AllArea);
defineVueElement(LOAN_APPROVAL_PAGE_ELEMENTS.body, BodyArea);
defineVueElement(LOAN_APPROVAL_PAGE_ELEMENTS.information, InformationArea);
defineVueElement(LOAN_APPROVAL_PAGE_ELEMENTS.actions, ActionsArea);
