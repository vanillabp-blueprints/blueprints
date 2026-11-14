package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.externalsystems.esignature.SignedContracts;

/**
 * What the e-signature service tells the application. Like {@link ApiController} and
 * {@link WorkflowTaskHandler}, this is a way in: something outside triggers, and the trigger
 * becomes a call to {@link Service}.
 *
 * <p>
 * The interface comes from the port, so the adapter which takes the callback can call it. The
 * module implements it and still knows nothing about the adapter, its endpoint or its JSON.
 * </p>
 */
@Component
public class SignedContractsHandler implements SignedContracts {

  @Autowired
  private Service loanApproval;

  @Override
  public boolean signed(
      final String contractId) {

    return loanApproval.contractSigned(contractId);

  }

}
