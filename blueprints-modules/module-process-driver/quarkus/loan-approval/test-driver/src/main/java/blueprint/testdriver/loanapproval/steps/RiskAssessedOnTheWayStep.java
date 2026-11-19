package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The branch of a large amount. If the test data chose it, somebody has to assess the risk
 * before the contract goes out, and this step does it on the way. For a small amount it does
 * nothing. So a scenario for a later point of the process works for both amounts, and the test
 * does not have to know about the branch.
 */
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class RiskAssessedOnTheWayStep implements Step {

  @Inject
  LoanApprovalApi loanApproval;

  @Inject
  StepSupport support;

  /**
   * The amount of the loan request decides whether there is a risk to assess.
   */
  @Override
  public List<Class<? extends Step>> prerequisites() {

    return List.of(LoanRequestedStep.class);

  }

  @Override
  public String description() {

    return "somebody assesses the risk, if the amount needs it";

  }

  @Override
  public void run(
      final Scenario scenario) {

    if (!support.loanRequestOf(scenario).getRiskAssessmentNeeded()) {
      return;
    }

    // Wait until the process is at the task, then complete it. The id of the task is only
    // there once the task is open, so waiting for the id is waiting for the wait state.
    final var taskId = support
        .awaitLoanRequest(
            scenario,
            this,
            loanRequest -> loanRequest.getAssessRiskTaskId() != null,
            loanRequest -> "the workflow did not open the user task 'Assess risk'")
        .getAssessRiskTaskId();

    // A clerk submits the form of the task.
    loanApproval.completeAssessRisk(scenario.getLoanRequestId(), taskId);

  }

}
