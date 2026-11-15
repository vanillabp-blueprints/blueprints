// Visual entry AssessRiskForm: the form of the user task "Assess risk". It exports nothing. When
// the browser evaluates it, it defines the four web components the data entry UserTaskForm names.

import { loanApproval, type AssessRiskForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.ts';
import { ASSESS_RISK_ELEMENTS } from '../loan-approval.ts';
import AssessRiskInformation from './AssessRiskInformation.ce.vue';

defineInputForm<AssessRiskForm>(ASSESS_RISK_ELEMENTS, {
  testId: 'assess-risk',
  label: (texts) => texts.riskAssessment,
  load: (userTaskId) => loanApproval.getAssessRiskForm(userTaskId),
  initialInput: (form) => form.riskAssessment ?? '',
  complete: (userTaskId, riskAssessment) => loanApproval.completeAssessRisk(userTaskId, { riskAssessment }),
  information: AssessRiskInformation,
});
