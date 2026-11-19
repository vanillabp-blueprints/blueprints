package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.Scenario;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The user task "Check contract" is open, but the signature of the customer has not come back
 * yet. The task must not be completed in this situation. The customer of this state signs only
 * when the test makes the simulator sign.
 */
@QuarkusTest
@WithTestResource(EverySystemIsTheSimulator.class)
public class CheckContractWithoutSignatureIT extends ScenarioTest {

  private Scenario scenario;

  private String taskId;

  @BeforeEach
  public void theContractWaitsForTheSignature() {

    scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE);
    taskId = driver.openUserTaskId(scenario);

  }

  @Test
  public void aClerkCannotApproveTheLoanBeforeTheCustomerSigned() {

    assertThat(reasonOfTheRefusal(this::aClerkChecksTheContractInTheBrowser)).contains("not signed yet");

    theTaskIsStillOpen();

  }

  @Test
  public void aClerkApprovesTheLoanInTheBrowserOnceTheCustomerSigned() {

    assertThat(reasonOfTheRefusal(this::aClerkChecksTheContractInTheBrowser)).contains("not signed yet");

    theCustomerSigns();
    aClerkChecksTheContractInTheBrowser();

    theLoanIsApproved();

  }

  @Test
  public void theFormOfTheTaskApprovesTheLoanOnceTheCustomerSigned() {

    assertThat(reasonOfTheRefusal(this::theFormOfTheTaskSubmitsTheCheck)).contains("not signed yet");

    theCustomerSigns();
    theFormOfTheTaskSubmitsTheCheck();

    theLoanIsApproved();

  }

  private void aClerkChecksTheContractInTheBrowser() {

    loanApproval.checkContract(scenario.getLoanRequestId(), taskId);

  }

  private void theFormOfTheTaskSubmitsTheCheck() {

    loanApproval.completeCheckContract(scenario.getLoanRequestId(), taskId);

  }

  private void theCustomerSigns() {

    assertThat(simulator()
        .sign(scenario.getLoanRequestId())
        .getAnswer()).as("the application took the signature").isEqualTo(REPORT_TAKEN);

  }

  private void theTaskIsStillOpen() {

    assertThat(loanApprovals.findByIdOptional(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(taskId);
          assertThat(loanRequest.getContractChecked()).isNull();
        });

  }

  private void theLoanIsApproved() {

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        scenario.getLoanRequestId(),
        loanApproval -> Boolean.TRUE.equals(loanApproval.getContractChecked()));
    assertThat(loanRequest.getContractSigned()).isTrue();
    assertThat(loanRequest.getCheckContractTaskId()).isNull();

  }

}
