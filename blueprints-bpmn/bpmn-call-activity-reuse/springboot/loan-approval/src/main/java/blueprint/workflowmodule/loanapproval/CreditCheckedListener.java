package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.creditcheck.CreditChecked;

/**
 * Where the result of the credit check enters the loan approval: an event arrives, and this
 * class turns it into a call to {@link Service}.
 *
 * <p>
 * It is a driving adapter, like {@link ApiController}. It exists so that the credit check
 * does not need to know who asked. Every process which uses the credit check gets every
 * result, so the listener ignores the results other processes asked for.
 * </p>
 */
@Component
public class CreditCheckedListener {

  @Autowired
  private Service loanApproval;

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

    loanApproval.creditChecked(creditCheck.caseId(), creditCheck.score());

  }

}
