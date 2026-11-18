package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.TestData;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The user task "Assess risk". Only a large amount leads there.
 */
@QuarkusTest
@WithTestResource(EverySystemIsTheSimulator.class)
public class AssessRiskIT extends ScenarioTest {

  private Scenario scenario;

  private String taskId;

  @BeforeEach
  public void aLargeLoanWaitsForItsRiskAssessment() {

    scenario = driver.advanceTo(
        ProcessState.ASSESS_RISK_USER_TASK,
        TestData
            .defaults()
            .withAmount(TestData.LARGE_AMOUNT));
    taskId = driver.openUserTaskId(scenario);

  }

  @Test
  public void theCockpitShowsTheTaskWithTheAmount() {

    final var userTask = awaitUserTask(taskId, task -> true);

    assertThat(userTask.getTaskDefinition()).isEqualTo("assessRisk");
    assertThat(userTask.getDetails())
        .isEqualTo(Map.of(
            WorkflowTaskHandler.AMOUNT, TestData.LARGE_AMOUNT,
            WorkflowTaskHandler.CREDIT_SCORE, TestData.GOOD_CREDIT_SCORE));

  }

  @Test
  public void noContractGoesOutBeforeTheRiskIsAssessed() {

    assertThat(loanApprovals.findByIdOptional(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> assertThat(loanRequest.getContractSentForSigning()).isNull());
    assertThat(simulator().envelope(scenario.getLoanRequestId())).isEmpty();

  }

  @Test
  public void aClerkAssessesTheRiskInTheBrowser() {

    assertThat(loanApproval.assessRisk(scenario.getLoanRequestId(), taskId)).contains("assessed");

    theContractGoesOutForSigning();

  }

  @Test
  public void theFormOfTheTaskSubmitsTheAssessment() {

    assertThat(loanApproval.completeAssessRisk(scenario.getLoanRequestId(), taskId)).contains("assessed");

    theContractGoesOutForSigning();

  }

  private void theContractGoesOutForSigning() {

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        scenario.getLoanRequestId(),
        loanApproval -> loanApproval.getCheckContractTaskId() != null);
    assertThat(loanRequest.getRiskAssessed()).isTrue();
    assertThat(simulator().envelope(scenario.getLoanRequestId()))
        .hasValueSatisfying(envelope -> {
          assertThat(envelope.getSigner()).isEqualTo(scenario.getTestData().customerName());
          assertThat(envelope.getDocument()).contains(String.valueOf(TestData.LARGE_AMOUNT));
        });
    // The task is done, so the cockpit took it off the list.
    awaitUserTask(taskId, task -> task.getEndedAt() != null);

  }

}
