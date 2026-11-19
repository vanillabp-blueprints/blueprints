package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.TestData;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The user task "Check contract", once the customer signed. Two scenarios lead there: a small
 * amount goes straight to it, a large amount passes "Assess risk" first. The test does not know
 * that. The driver completes "Assess risk" on the way.
 */
@QuarkusTest
@WithTestResource(EverySystemIsTheSimulator.class)
public class CheckContractIT extends ScenarioTest {

  @Test
  public void aSmallLoanIsApprovedWithoutARiskAssessment() {

    final var scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK);

    assertThat(loanApprovals.findByIdOptional(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> assertThat(loanRequest.getRiskAssessed()).isNull());
    aClerkApprovesTheLoan(scenario);

  }

  @Test
  public void aLargeLoanIsApprovedAfterItsRiskAssessment() {

    final var scenario = driver.advanceTo(
        ProcessState.CHECK_CONTRACT_USER_TASK,
        TestData
            .defaults()
            .withAmount(TestData.LARGE_AMOUNT));

    assertThat(loanApprovals.findByIdOptional(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> assertThat(loanRequest.getRiskAssessed()).isTrue());
    aClerkApprovesTheLoan(scenario);

  }

  @Test
  public void theCockpitShowsTheTask() {

    final var scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK);

    final var userTask = awaitUserTask(driver.openUserTaskId(scenario), task -> true);
    assertThat(userTask.getWorkflowModuleId()).isEqualTo("loan-approval");
    assertThat(userTask.getTaskDefinition()).isEqualTo("checkContract");

  }

  @Test
  public void aSignatureIsTakenOnceEvenIfItArrivesTwice() {

    final var scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK);

    // A service may report again, for example after it lost the first answer.
    assertThat(simulator()
        .sign(scenario.getLoanRequestId())
        .getAnswer()).as("the application took the second report").isEqualTo(REPORT_TAKEN);
    assertThat(loanApprovals.findByIdOptional(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getContractSigned()).isTrue();
          assertThat(loanRequest.getCheckContractTaskId()).isNotNull();
        });

  }

  private void aClerkApprovesTheLoan(
      final Scenario scenario) {

    final var taskId = driver.openUserTaskId(scenario);

    assertThat(loanApproval.completeCheckContract(scenario.getLoanRequestId(), taskId)).contains("approved");

    assertThat(awaitAggregate(
        loanApprovals::findByIdOptional,
        scenario.getLoanRequestId(),
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractChecked()))
        .getCheckContractTaskId()).isNull();
    // The workflow ended, so the cockpit took the user task off the list.
    awaitUserTask(taskId, task -> task.getEndedAt() != null);

  }

}
