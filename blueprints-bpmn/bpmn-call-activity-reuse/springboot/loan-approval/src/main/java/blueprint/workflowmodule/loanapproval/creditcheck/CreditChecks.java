package blueprint.workflowmodule.loanapproval.creditcheck;

/**
 * How another process asks for a credit check. Together with {@link CreditChecked} it is all
 * a process which uses the credit check gets to see.
 *
 * <p>
 * The request is a method call, the result is an event. The credit check could not be
 * reused if it called its users back, because then it would have to know every one of them.
 * </p>
 */
public interface CreditChecks {

  /**
   * Starts a credit check. The result comes back later as a {@link CreditChecked} event.
   *
   * @param requestedBy Who asks. It comes back unchanged with the result.
   * @param caseId      The id of the case which needs the check. It comes back unchanged
   *                    with the result.
   * @param amount      The amount the credit is checked for.
   * @return The id of the credit check started.
   */
  String request(
      String requestedBy,
      String caseId,
      int amount);

}
