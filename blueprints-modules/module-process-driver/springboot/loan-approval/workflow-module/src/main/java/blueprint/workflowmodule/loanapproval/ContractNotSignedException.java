package blueprint.workflowmodule.loanapproval;

/**
 * Somebody wants to complete the user task "Check contract", but the customer has not signed the
 * contract yet. The task stays open, and the API answers with 409.
 */
public class ContractNotSignedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Names the loan approval in the message.
   *
   * @param loanRequestId The loan approval whose contract is not signed yet.
   */
  public ContractNotSignedException(
      final String loanRequestId) {

    super("The contract of loan approval '"
        + loanRequestId
        + "' is not signed yet. Check it once the customer signed it.");

  }

}
