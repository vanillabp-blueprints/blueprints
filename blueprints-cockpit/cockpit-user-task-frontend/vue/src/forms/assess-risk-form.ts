// Visual entry AssessRiskForm: the form of the user task "Assess risk". It exports nothing. When
// the browser evaluates it, it defines the four web components the data entry UserTaskForm names.

import { loanApproval, type AssessRiskForm } from '@gui-client';
import { defineInputForm } from '@shared';
import { ASSESS_RISK_ELEMENTS } from '@loan-approval';
import AssessRiskInformation from './AssessRiskInformation.ce.vue';

defineInputForm<AssessRiskForm>(ASSESS_RISK_ELEMENTS, {
  testId: 'assess-risk',
  label: (texts) => texts.riskAssessment,
  load: (loanRequestId, taskId) => loanApproval.getAssessRiskForm({ loanRequestId, taskId }),
  initialInput: (form) => form.riskAssessment ?? '',
  complete: (loanRequestId, taskId, riskAssessment) => loanApproval.completeAssessRisk({ loanRequestId, taskId, assessRiskInput: { riskAssessment } }),
  information: AssessRiskInformation,
});
