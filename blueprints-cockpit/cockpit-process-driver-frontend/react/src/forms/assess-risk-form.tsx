// Visual entry AssessRiskForm: the form of the user task "Assess risk". It exports nothing. When
// the browser evaluates it, it defines the three web components the data entry UserTaskForm names.

import { loanApproval } from '@gui-client';
import { ASSESS_RISK_ELEMENTS } from '@loan-approval';
import { defineTaskForm } from '@shared';

defineTaskForm(ASSESS_RISK_ELEMENTS, {
  testId: 'assess-risk',
  completeLabel: (texts) => texts.riskAssessed,
  complete: (loanRequestId, taskId) => loanApproval.assessRisk({ loanRequestId, taskId }),
});
