// Visual entry AssessRiskForm: the form of the user task "Assess risk". It exports nothing. When
// the browser evaluates it, it defines the four web components the data entry UserTaskForm names.

import { loanApprovalApi, type AssessRiskForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.tsx';
import type { Texts } from '../shared/texts.ts';
import { ASSESS_RISK_ELEMENTS } from '../loan-approval.ts';

function LoanRequest({ form, texts, language }: { form: AssessRiskForm; texts: Texts; language: string }) {
  return (
    <dl>
      <dt>{texts.loanRequest}</dt>
      <dd data-testid="assess-risk-loan-request">{form.loanRequestId}</dd>
      <dt>{texts.amount}</dt>
      <dd data-testid="assess-risk-amount">{form.amount.toLocaleString(language)}</dd>
    </dl>
  );
}

defineInputForm<AssessRiskForm>(ASSESS_RISK_ELEMENTS, {
  testId: 'assess-risk',
  label: (texts) => texts.riskAssessment,
  load: loanApprovalApi.getAssessRiskForm,
  initialInput: (form) => form.riskAssessment ?? '',
  complete: loanApprovalApi.completeAssessRisk,
  Information: LoanRequest,
});
