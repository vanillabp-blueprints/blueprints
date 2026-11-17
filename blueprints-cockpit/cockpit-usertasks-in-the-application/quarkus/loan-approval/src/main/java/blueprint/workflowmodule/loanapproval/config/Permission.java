package blueprint.workflowmodule.loanapproval.config;

/**
 * The authorities of this workflow module, one per kind of user task. A user who holds one
 * may read, save and complete the user tasks of that kind, and nobody else may.
 *
 * <p>
 * The module checks them itself, on its own endpoints (see
 * {@link MethodSecurityConfiguration}). How a user gets an authority is up to the
 * application: from an identity provider, from a role, or from a list of demo users as in
 * this blueprint. The names start with the module, so two modules in one application cannot
 * mean different things by the same name.
 * </p>
 */
public final class Permission {

  /** May work on the user task "Assess risk". */
  public static final String ASSESS_RISK = "LOAN_APPROVAL_ASSESS_RISK";

  /** May work on the user task "Check document". */
  public static final String CHECK_DOCUMENT = "LOAN_APPROVAL_CHECK_DOCUMENT";

  private Permission() {
  }

}
