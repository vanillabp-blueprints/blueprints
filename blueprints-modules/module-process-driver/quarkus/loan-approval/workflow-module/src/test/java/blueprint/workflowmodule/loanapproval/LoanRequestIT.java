package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.simulator.testapi.client.model.SignatureStatus;
import blueprint.testdriver.loanapproval.TestData;
import blueprint.workflowmodule.loanapproval.api.client.model.Answer;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * What happens before a workflow starts, and a report nobody expects. These cases need no
 * process driver, because no workflow is waiting anywhere.
 */
@QuarkusTest
@WithTestResource(EverySystemIsTheSimulator.class)
public class LoanRequestIT extends ScenarioTest {

  @Test
  public void aCustomerWithALowCreditScoreIsDeclinedAtOnce() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 480);

    final var answer = theCustomerRequestsALoan(customerId);

    assertThat(answer.getOutcome()).isEqualTo("declined");
    assertThat(loanApprovals.findByIdOptional(answer.getLoanRequestId())).isEmpty();

  }

  @Test
  public void aCustomerTheCreditRatingDoesNotKnowIsDeclinedAtOnce() {

    // A reserved id the credit rating was never told about.
    final var customerId = simulator().nextCustomerId();

    final var answer = theCustomerRequestsALoan(customerId);

    assertThat(answer.getOutcome()).isEqualTo("declined");
    assertThat(loanApprovals.findByIdOptional(answer.getLoanRequestId())).isEmpty();

  }

  /**
   * The e-signature service reports a signature of a contract the application never sent, for
   * example because another system uses the same callback address. The simulator sends such a
   * report on request of the test API.
   */
  @Test
  public void aSignatureOfAnUnknownContractIsRefused() {

    final var unknownContract = UUID
        .randomUUID()
        .toString();

    final var report = simulator().sendCallback(unknownContract, SignatureStatus.SIGNED, callbackUrl);

    assertThat(report.getAnswer()).as("the application does not know the contract")
        .isEqualTo(REPORT_OF_AN_UNKNOWN_CONTRACT);
    assertThat(loanApprovals.findByIdOptional(unknownContract)).isEmpty();

  }

  private Answer theCustomerRequestsALoan(
      final String customerId) {

    return loanApproval.request(customerId, TestData.JANE_DOE, TestData.SMALL_AMOUNT);

  }

}
