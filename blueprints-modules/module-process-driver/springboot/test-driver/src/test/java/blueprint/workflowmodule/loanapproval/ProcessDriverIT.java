package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.TestData;
import blueprint.testdriver.loanapproval.api.model.Reached;
import blueprint.testdriver.loanapproval.steps.ProcessStateNotReachedException;

/**
 * The process driver itself: its REST API, which the dev shell and the tests of a user interface
 * use, and the reason it gives when a state cannot be reached.
 */
public class ProcessDriverIT extends ScenarioTest {

  private final RestClient processDriver = RestClient.create("http://localhost:"
      + PORT
      + "/process-driver");

  @Test
  public void itListsItsStates() {

    assertThat(processDriver
        .get()
        .uri("/states")
        .retrieve()
        .body(new ParameterizedTypeReference<List<String>>() {
        }))
        .containsExactly(
            "ASSESS_RISK_USER_TASK",
            "CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE",
            "CHECK_CONTRACT_USER_TASK");

  }

  /**
   * A request of the REST API keeps one entity manager open while it runs. The driver must still
   * see what the workflow writes meanwhile, and the large amount makes it complete a task on the
   * way.
   */
  @Test
  public void itBringsALoanApprovalToAStateOverHttp() {

    final var reached = processDriver
        .get()
        .uri("/advance-to?state=CHECK_CONTRACT_USER_TASK&amount=20000")
        .retrieve()
        .body(Reached.class);

    assertThat(reached.state()).isEqualTo("CHECK_CONTRACT_USER_TASK");
    assertThat(loanApprovals.findById(reached.loanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(reached.userTaskId());
          assertThat(loanRequest.getCustomerId()).isEqualTo(reached.customerId());
          assertThat(loanRequest.getRiskAssessed()).isTrue();
          assertThat(loanRequest.getContractSigned()).isTrue();
        });

  }

  @Test
  public void anUnknownStateIsRefusedWithTheKnownOnes() {

    final var answer = processDriver
        .get()
        .uri("/advance-to?state=SOMEWHERE")
        .exchange((
            request,
            response) -> response
                .getStatusCode()
                .value()
                + " "
                + response.bodyTo(String.class));

    assertThat(answer).startsWith("400").contains("CHECK_CONTRACT_USER_TASK");

  }

  @Test
  public void aStateTheTestDataCannotReachIsRefusedWithTheReason() {

    // The default test data names a small amount, which never leads to "Assess risk".
    assertThatThrownBy(() -> driver.advanceTo(ProcessState.ASSESS_RISK_USER_TASK, TestData.defaults()))
        .isInstanceOf(ProcessStateNotReachedException.class)
        .hasMessageContaining("Assess risk")
        .hasMessageContaining("below the limit of 10000");

  }

}
