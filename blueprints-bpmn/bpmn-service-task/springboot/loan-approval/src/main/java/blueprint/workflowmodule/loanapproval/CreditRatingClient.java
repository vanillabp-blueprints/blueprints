package blueprint.workflowmodule.loanapproval;

/**
 * The surrounding system the service task talks to. It is an interface owned by this
 * workflow module, so the test can put a simulator in its place and the process can be
 * played through without a rating service being available.
 *
 * <p>
 * Nothing about it is VanillaBP specific. A service task calls business code, and
 * business code calls whatever it needs.
 * </p>
 */
public interface CreditRatingClient {

  /**
   * Asks the rating provider what a loan request is worth.
   *
   * @param provider      The provider to ask, coming from the BPMN input mapping.
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   * @return The credit rating.
   * @throws CreditRatingUnavailableException The provider could not be reached. The task
   *                                         handler lets this through, and the BPMS
   *                                         retries the task.
   */
  int rate(
      String provider,
      String loanRequestId,
      int amount);

}
