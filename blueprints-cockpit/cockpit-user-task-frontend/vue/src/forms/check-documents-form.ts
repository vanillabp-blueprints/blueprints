// Visual entry CheckDocumentsForm: the form of the user task "Check documents". It shows the risk
// assessment of the user task before, which it may not change, and asks for a note.

import { loanApproval, type CheckDocumentsForm } from '@gui-client';
import { defineInputForm } from '@shared';
import { CHECK_DOCUMENTS_ELEMENTS } from '@loan-approval';
import CheckDocumentsInformation from './CheckDocumentsInformation.ce.vue';

defineInputForm<CheckDocumentsForm>(CHECK_DOCUMENTS_ELEMENTS, {
  testId: 'check-documents',
  label: (texts) => texts.documentsNote,
  load: (loanRequestId, taskId) => loanApproval.getCheckDocumentsForm({ loanRequestId, taskId }),
  initialInput: (form) => form.documentsNote ?? '',
  complete: (loanRequestId, taskId, documentsNote) => loanApproval.completeCheckDocuments({ loanRequestId, taskId, checkDocumentsInput: { documentsNote } }),
  information: CheckDocumentsInformation,
});
