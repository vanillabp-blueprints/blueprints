// Visual entry CheckContractForm: the form of the user task "Check contract". The module refuses
// to complete it with 409 while the customer has not signed. The form says so, and the task stays
// open.

import { loanApproval } from '@gui-client';
import { CHECK_CONTRACT_ELEMENTS } from '@loan-approval';
import { defineTaskForm } from '@shared';

defineTaskForm(CHECK_CONTRACT_ELEMENTS, {
  testId: 'check-contract',
  completeLabel: (texts) => texts.approve,
  complete: (loanRequestId, taskId) => loanApproval.completeCheckContract({ loanRequestId, taskId }),
});
