package blueprint.workflowmodule.loanapproval.overdraft;

import blueprint.workflowmodule.loanapproval.creditcheck.CreditChecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Where the result of the credit check enters the overdraft request. It ignores the results
 * other processes asked for.
 */
@ApplicationScoped
public class CreditCheckedListener {

  @Inject
  Service overdraftRequest;

  /**
   * The credit check reported a result.
   *
   * @param creditCheck What the credit check worked out, and for whom.
   */
  public void onCreditChecked(
      @Observes final CreditChecked creditCheck) {

    if (!Service.CREDIT_CHECK_REQUESTER.equals(creditCheck.requestedBy())) {
      return;
    }

    overdraftRequest.creditChecked(creditCheck.caseId(), creditCheck.score());

  }

}
