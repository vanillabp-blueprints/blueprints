package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    assertThat(checkContract()).isEqualTo(409);

    assertThat(loanApprovals.findById(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(taskId);
          assertThat(loanRequest.getContractChecked()).isNull();
        });

  }

  @Test
  public void theTaskCanBeCompletedOnceTheCustomerSigned() {

    assertThat(checkContract()).isEqualTo(409);

    assertThat(simulator().sign(scenario.getLoanRequestId())).startsWith("200").contains("204");

    assertThat(checkContract()).isEqualTo(200);
    awaitAggregate(
        loanApprovals,
        scenario.getLoanRequestId(),
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractChecked()));

  }

  private int checkContract() {

    return api
        .get()
        .uri("/{loanRequestId}/check-contract/{taskId}", scenario.getLoanRequestId(), taskId)
        .exchange((
            request,
            response) -> response
                .getStatusCode()
                .value());

  }

}
