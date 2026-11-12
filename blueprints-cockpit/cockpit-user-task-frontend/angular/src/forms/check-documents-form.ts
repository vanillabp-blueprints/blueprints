// Visual entry CheckDocumentsForm: the form of the user task "Check documents". It shows the risk
// assessment of the user task before, which it may not change, and asks for a note.

import { Component, input } from '@angular/core';
import { loanApprovalApi, type CheckDocumentsForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.ts';
import type { Texts } from '../shared/texts.ts';
import { CHECK_DOCUMENTS_ELEMENTS } from '../loan-approval.ts';

@Component({
  // Not the name of a web component of this form: that element would get the web component.
  selector: 'loan-approval-check-documents-loan-request',
  template: `
    <dl>
      <dt>{{ texts().loanRequest }}</dt>
      <dd data-testid="check-documents-loan-request">{{ form().loanRequestId }}</dd>
      <dt>{{ texts().riskAssessment }}</dt>
      <dd data-testid="check-documents-risk">{{ form().riskAssessment }}</dd>
    </dl>`,
})
class LoanRequest {
  readonly form = input.required<CheckDocumentsForm>();
  readonly texts = input.required<Texts>();
  readonly language = input.required<string>();
}

defineInputForm<CheckDocumentsForm>(CHECK_DOCUMENTS_ELEMENTS, {
  testId: 'check-documents',
  label: (texts) => texts.documentsNote,
  load: loanApprovalApi.getCheckDocumentsForm,
  initialInput: (form) => form.documentsNote ?? '',
  complete: loanApprovalApi.completeCheckDocuments,
  information: LoanRequest,
});
