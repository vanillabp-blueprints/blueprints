package blueprint.testdriver.loanapproval.steps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.workflowmodule.loanapproval.Service;

/**
 * The branch of a large amount. If the test data chose it, somebody has to assess the risk
 * before the contract goes out, and this step does it on the way. For a small amount it does
 * nothing. So a scenario for a later point of the process works for both amounts, and the test
 * does not have to know about the branch.
 */
@Component
public class RiskAssessedOnTheWayStep implements Step {

  @Autowired
  private Service loanApproval;

  @Autowired
  private StepSupport support;

  @Override
  public String description() {

    return "somebody assesses the risk, if the amount needs it";

  }

  @Override
  public void run(
      final Scenario scenario) {

    if (!support.loanApprovalOf(scenario).isRiskAssessmentNeeded()) {
      return;
    }

    // Wait until the process is at the task, then complete it. The id of the task is only
    // there once the task is open, so waiting for the id is waiting for the wait state.
    final var taskId = support
        .awaitLoanApproval(
            scenario,
            this,
            loanRequest -> loanRequest.getAssessRiskTaskId() != null,
            loanRequest -> "the workflow did not open the user task 'Assess risk'")
        .getAssessRiskTaskId();

    support.inOwnTransaction(() -> loanApproval.assessRisk(scenario.getLoanRequestId(), taskId));

  }

}
