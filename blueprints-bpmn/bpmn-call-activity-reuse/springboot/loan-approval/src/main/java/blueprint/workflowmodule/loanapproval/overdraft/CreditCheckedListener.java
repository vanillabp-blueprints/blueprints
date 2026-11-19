package blueprint.workflowmodule.loanapproval.overdraft;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.creditcheck.CreditChecked;

/**
 * Where the result of the credit check enters the overdraft request. It ignores the results
 * other processes asked for.
 */
@Component("overdraftCreditCheckedListener")
public class CreditCheckedListener {

  @Autowired
  private Service overdraftRequest;

  /**
   * The credit check reported a result.
   *
   * @param creditCheck What the credit check worked out, and for whom.
   */
  @EventListener
  public void onCreditChecked(
      final CreditChecked creditCheck) {

    if (!Service.CREDIT_CHECK_REQUESTER.equals(creditCheck.requestedBy())) {
      return;
    }

    overdraftRequest.creditChecked(creditCheck.caseId(), creditCheck.score());

  }

}
