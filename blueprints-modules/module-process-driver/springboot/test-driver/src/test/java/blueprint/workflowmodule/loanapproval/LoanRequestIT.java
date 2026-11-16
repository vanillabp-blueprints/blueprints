package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import blueprint.workflowmodule.loanapproval.api.model.Answer;

/**
 * What happens before a workflow starts, and a callback nobody expects. These cases need no
 * process driver, because no workflow is waiting anywhere.
 */
public class LoanRequestIT extends ScenarioTest {

  @Test
  public void aCustomerWithALowCreditScoreIsDeclinedAtOnce() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 480);

    final var answer = request(customerId);

    assertThat(answer.outcome()).isEqualTo("declined");
    assertThat(loanApprovals.findById(answer.loanRequestId())).isEmpty();

  }

  @Test
  public void aCustomerTheCreditRatingDoesNotKnowIsDeclinedAtOnce() {

    // A reserved id the credit rating was never told about.
    final var customerId = simulator().nextCustomerId();

    final var answer = request(customerId);

    assertThat(answer.outcome()).isEqualTo("declined");
    assertThat(loanApprovals.findById(answer.loanRequestId())).isEmpty();

  }

  @Test
  public void aSignatureOfAnUnknownContractIsRefused() {

    final var status = RestClient
        .create("http://localhost:"
            + PORT)
        .post()
        .uri("/e-signature/callback")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("contractId", UUID.randomUUID().toString(), "status", "SIGNED"))
        .exchange((
            request,
            response) -> response
                .getStatusCode()
                .value());

    assertThat(status).isEqualTo(404);

  }

  private Answer request(
      final String customerId) {

    return api
        .get()
        .uri("/request?customerId={customerId}&amount=5000", customerId)
        .retrieve()
        .body(Answer.class);

  }

}
