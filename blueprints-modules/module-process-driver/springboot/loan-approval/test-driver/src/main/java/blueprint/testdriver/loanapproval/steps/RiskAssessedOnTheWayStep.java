package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;

/**
 * The branch of a large amount. If the test data chose it, somebody has to assess the risk
 * before the contract goes out, and this step does it on the way. For a small amount it does
 * nothing. So a scenario for a later point of the process works for both amounts, and the test
 * does not have to know about the branch.
 */
@Component
public class RiskAssessedOnTheWayStep implements Step {

  @Autowired
  private LoanApprovalApi loanApproval;

  @Autowired
  private StepSupport support;

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

    if (!support.loanRequestOf(scenario).riskAssessmentNeeded()) {
      return;
    }

    // Wait until the process is at the task, then complete it. The id of the task is only
    // there once the task is open, so waiting for the id is waiting for the wait state.
    final var taskId = support
        .awaitLoanRequest(
            scenario,
            this,
            loanRequest -> loanRequest.assessRiskTaskId() != null,
            loanRequest -> "the workflow did not open the user task 'Assess risk'")
        .assessRiskTaskId();

    // A clerk submits the form of the task.
    loanApproval.completeAssessRisk(scenario.getLoanRequestId(), taskId);

  }

}
