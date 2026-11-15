// Visual entry CheckDocumentsForm: the form of the user task "Check documents". It shows the risk
// assessment of the user task before, which it may not change, and asks for a note.

import { userTasksApi, type CheckDocumentsForm } from '../shared/api.ts';
import { defineInputForm } from '../shared/input-form.ts';
import { CHECK_DOCUMENTS_ELEMENTS } from '../loan-approval.ts';
import CheckDocumentsInformation from './CheckDocumentsInformation.ce.vue';

defineInputForm<CheckDocumentsForm>(CHECK_DOCUMENTS_ELEMENTS, {
  testId: 'check-documents',
  label: (texts) => texts.documentsNote,
  load: (userTaskId) => userTasksApi.getCheckDocumentsForm({ userTaskId }),
  initialInput: (form) => form.documentsNote ?? '',
  complete: (userTaskId, documentsNote) => userTasksApi.completeCheckDocuments({ userTaskId, checkDocumentsInput: { documentsNote } }),
  information: CheckDocumentsInformation,
});
