package blueprint.workflowmodule.loanapproval.config;

/**
 * The authorities of this workflow module. Each kind of user task has one, and two more are for
 * the people who start a loan approval and who hand a customer to another advisor.
 *
 * <p>
 * Each constant is used twice, and that is the point. It protects the endpoints of a form
 * ({@code @Secured} and {@code @PreAuthorize}), and it is the candidate group the Business
 * Cockpit gets for a user task of that kind. So the cockpit offers a task to exactly the people
 * the form lets in. Two lists, one for the endpoints and one for the cockpit, would drift apart.
 * </p>
 *
 * <p>
 * How a user gets an authority is up to the application: from an identity provider, from a
 * role, or from a list of demo users as in this blueprint. The names start with the module, so
 * two modules in one application cannot mean different things by the same name.
 * </p>
 */
public final class Permission {

  /** May request a loan for a customer, and becomes the advisor of that customer. */
  public static final String REQUEST_LOAN = "LOAN_APPROVAL_REQUEST_LOAN";

  /** May hand a customer over to another advisor. */
  public static final String CHANGE_ADVISOR = "LOAN_APPROVAL_CHANGE_ADVISOR";

  /** May work on the user task "Call customer". */
  public static final String CALL_CUSTOMER = "LOAN_APPROVAL_CALL_CUSTOMER";

  /** May work on the user task "Assess risk". */
  public static final String ASSESS_RISK = "LOAN_APPROVAL_ASSESS_RISK";

  /** May work on the user task "Approve loan". */
  public static final String APPROVE_LOAN = "LOAN_APPROVAL_APPROVE_LOAN";

  private Permission() {
  }

}
