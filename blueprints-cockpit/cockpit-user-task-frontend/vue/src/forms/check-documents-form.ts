// Visual entry CheckDocumentsForm: the form of the user task "Check documents". It shows the risk
// assessment of the user task before, which it may not change, and asks for a note.

import { loanApprovalApi, type CheckDocumentsForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.ts';
import { CHECK_DOCUMENTS_ELEMENTS } from '../loan-approval.ts';
import CheckDocumentsInformation from './CheckDocumentsInformation.ce.vue';

defineInputForm<CheckDocumentsForm>(CHECK_DOCUMENTS_ELEMENTS, {
  testId: 'check-documents',
  label: (texts) => texts.documentsNote,
  load: loanApprovalApi.getCheckDocumentsForm,
  initialInput: (form) => form.documentsNote ?? '',
  complete: loanApprovalApi.completeCheckDocuments,
  information: CheckDocumentsInformation,
});
