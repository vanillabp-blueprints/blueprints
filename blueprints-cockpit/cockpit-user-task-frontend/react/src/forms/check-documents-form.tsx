// Visual entry CheckDocumentsForm: the form of the user task "Check documents". It shows the risk
// assessment of the user task before, which it may not change, and asks for a note.

import { loanApproval, type CheckDocumentsForm } from '@gui-client';
import { defineInputForm, type Texts } from '@shared';
import { CHECK_DOCUMENTS_ELEMENTS } from '@loan-approval';

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
  load: (loanRequestId, taskId) => loanApproval.getCheckDocumentsForm({ loanRequestId, taskId }),
  initialInput: (form) => form.documentsNote ?? '',
  complete: (loanRequestId, taskId, documentsNote) => loanApproval.completeCheckDocuments({ loanRequestId, taskId, checkDocumentsInput: { documentsNote } }),
  Information: LoanRequest,
});
