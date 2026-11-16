package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;

/**
 * Waits until the user task "Assess risk" is open. Only a large amount leads there, so the step
 * refuses test data with a small one at once, instead of waiting for a task which never comes.
 */
@Component
public class RiskAssessmentOpenedStep implements Step {

  @Autowired
  private LoanApprovalProperties properties;

  @Autowired
  private StepSupport support;

  /**
   * The task opens right after the loan request, if the amount is large.
   */
  @Override
  public List<Class<? extends Step>> prerequisites() {

    return List.of(LoanRequestedStep.class);

  }

  @Override
  public String description() {

    return "the user task 'Assess risk' opens";

  }

  @Override
  public void run(
      final Scenario scenario) {

    if (!support.loanApprovalOf(scenario).isRiskAssessmentNeeded()) {
      throw new ProcessStateNotReachedException(scenario, this, "the amount "
          + scenario.getTestData().amount()
          + " is below the limit of "
          + properties.getRiskAssessmentLimit()
          + ", so the workflow does not ask for a risk assessment. Use an amount of at least "
          + properties.getRiskAssessmentLimit()
          + " in the test data.");
    }

    support.awaitLoanApproval(
        scenario,
        this,
        loanRequest -> loanRequest.getAssessRiskTaskId() != null,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getRiskAssessed())
            ? "the risk is assessed already, so the workflow is past the task. Did somebody else complete it?"
            : "the workflow did not open the task. Look into the log of the application for an error"
                + " of the BPMS adapter.");

  }

}
