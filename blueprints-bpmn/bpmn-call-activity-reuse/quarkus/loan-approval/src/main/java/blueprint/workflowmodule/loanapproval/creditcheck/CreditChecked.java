package blueprint.workflowmodule.loanapproval.creditcheck;

/**
 * The result of a credit check. The credit check publishes it, and every process which asked
 * for one listens.
 *
 * @param requestedBy Who asked, as passed to {@link CreditChecks#request}.
 * @param caseId      The case which needed the check, as passed to
 *                    {@link CreditChecks#request}.
 * @param score       The score the credit agency gave.
 */
public record CreditChecked(
                            String requestedBy,
                            String caseId,
                            int score) {
}
