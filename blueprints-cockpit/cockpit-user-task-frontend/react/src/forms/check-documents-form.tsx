// Visual entry CheckDocumentsForm: the form of the user task "Check documents". It shows the risk
// assessment of the user task before, which it may not change, and asks for a note.

import { loanApprovalApi, type CheckDocumentsForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.tsx';
import type { Texts } from '../shared/texts.ts';
import { CHECK_DOCUMENTS_ELEMENTS } from '../loan-approval.ts';

function LoanRequest({ form, texts }: { form: CheckDocumentsForm; texts: Texts }) {
  return (
    <dl>
      <dt>{texts.loanRequest}</dt>
      <dd data-testid="check-documents-loan-request">{form.loanRequestId}</dd>
      <dt>{texts.riskAssessment}</dt>
      <dd data-testid="check-documents-risk">{form.riskAssessment}</dd>
    </dl>
  );
}

defineInputForm<CheckDocumentsForm>(CHECK_DOCUMENTS_ELEMENTS, {
  testId: 'check-documents',
  label: (texts) => texts.documentsNote,
  load: loanApprovalApi.getCheckDocumentsForm,
  initialInput: (form) => form.documentsNote ?? '',
  complete: loanApprovalApi.completeCheckDocuments,
  Information: LoanRequest,
});
