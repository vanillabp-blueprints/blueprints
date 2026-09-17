package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;

/**
 * The system the bank has to report its decisions to.
 *
 * <p>
 * A port, so a test can put a simulator in its place. What it receives is the loan
 * approval as it was when the decision was taken, not as it is today - that difference is
 * the whole reason this blueprint exists.
 * </p>
 */
public interface ComplianceArchive {

  /**
   * Reports one decision.
   *
   * @param event               What happened, as the notice names it.
   * @param loanApprovalAsItWas The loan approval in the state of that moment.
   */
  void store(
      String event,
      Aggregate loanApprovalAsItWas);

}
