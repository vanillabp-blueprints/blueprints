// Visual entry AssessRiskForm: the form of the user task "Assess risk". It exports nothing. When
// the browser evaluates it, it defines the four web components the data entry UserTaskForm names.

import { Component, input } from '@angular/core';
import { loanApprovalApi, type AssessRiskForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.ts';
import type { Texts } from '../shared/texts.ts';
import { ASSESS_RISK_ELEMENTS } from '../loan-approval.ts';

@Component({
  // Not the name of a web component of this form: that element would get the web component.
  selector: 'loan-approval-assess-risk-loan-request',
  template: `
    <dl>
      <dt>{{ texts().loanRequest }}</dt>
      <dd data-testid="assess-risk-loan-request">{{ form().loanRequestId }}</dd>
      <dt>{{ texts().amount }}</dt>
      <dd data-testid="assess-risk-amount">{{ form().amount.toLocaleString(language()) }}</dd>
    </dl>`,
})
class LoanRequest {
  readonly form = input.required<AssessRiskForm>();
  readonly texts = input.required<Texts>();
  readonly language = input.required<string>();
}

defineInputForm<AssessRiskForm>(ASSESS_RISK_ELEMENTS, {
  testId: 'assess-risk',
  label: (texts) => texts.riskAssessment,
  load: loanApprovalApi.getAssessRiskForm,
  initialInput: (form) => form.riskAssessment ?? '',
  complete: loanApprovalApi.completeAssessRisk,
  information: LoanRequest,
});
