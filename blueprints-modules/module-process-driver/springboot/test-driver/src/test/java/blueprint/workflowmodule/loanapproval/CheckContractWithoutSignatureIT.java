package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.Scenario;

/**
 * The user task "Check contract" is open, but the signature of the customer has not come back
 * yet. The task must not be completed in this situation.
 */
public class CheckContractWithoutSignatureIT extends ScenarioTest {

  private Scenario scenario;

  private String taskId;

  @BeforeEach
  public void theContractWaitsForTheSignature() {

    scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE);
    taskId = driver.openUserTaskId(scenario);

  }

  @Test
  public void theTaskCannotBeCompletedBeforeTheCustomerSigned() {

    assertThat(checkContract(HttpMethod.GET)).isEqualTo(409);

    assertThat(loanApprovals.findById(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(taskId);
          assertThat(loanRequest.getContractChecked()).isNull();
        });

  }

  @Test
  public void theTaskCanBeCompletedOnceTheCustomerSigned() {

    assertThat(checkContract(HttpMethod.GET)).isEqualTo(409);

    assertThat(simulator().sign(scenario.getLoanRequestId())).startsWith("200").contains("204");

    assertThat(checkContract(HttpMethod.GET)).isEqualTo(200);
    awaitAggregate(
        loanApprovals,
        scenario.getLoanRequestId(),
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractChecked()));

  }

  @Test
  public void theFormCompletesTheTaskWithPostOnceTheCustomerSigned() {

    assertThat(checkContract(HttpMethod.POST)).isEqualTo(409);

    assertThat(simulator().sign(scenario.getLoanRequestId())).startsWith("200").contains("204");

    assertThat(checkContract(HttpMethod.POST)).isEqualTo(200);
    awaitAggregate(
        loanApprovals,
        scenario.getLoanRequestId(),
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractChecked()));

  }

  /** A person in the browser sends GET, the form of the user task sends POST. */
  private int checkContract(
      final HttpMethod method) {

    return api
        .method(method)
        .uri("/{loanRequestId}/check-contract/{taskId}", scenario.getLoanRequestId(), taskId)
        .exchange((
            request,
            response) -> response
                .getStatusCode()
                .value());

  }

}
