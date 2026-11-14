package blueprint.workflowmodule.loanapproval;

/**
 * The neighbour system this workflow module asks for a credit score. The module knows it only
 * by this role. Whoever builds the application brings the implementation: a client of the real
 * credit bureau, or, in this blueprint, the load driver which plays the bureau.
 *
 * <p>
 * The question goes out in a service task, and the answer comes back later as a message,
 * through {@link Service#creditScoreReceived(String, int)}. It may come after a while, and it
 * may not come at all. The process does not wait for ever: after a while the clerk assesses the
 * loan without the score.
 * </p>
 *
 * <p>
 * An implementation must not answer inside this call. The call runs in the transaction of the
 * service task, so the workflow is not yet waiting for the answer.
 * </p>
 */
public interface CreditBureau {

  /**
   * Asks for the credit score of a customer. The answer comes later.
   *
   * @param loanRequestId The natural id of the loan request, which the answer names again.
   * @param amount        The amount requested.
   */
  void requestScore(
      String loanRequestId,
      int amount);

}
