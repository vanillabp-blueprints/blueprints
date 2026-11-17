package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.TestData;

/**
 * Waits until the user task "Assess risk" is open. Only a large amount leads there, so the step
 * refuses test data with a small one at once, instead of waiting for a task which never comes.
 */
@Component
public class RiskAssessmentOpenedStep implements Step {

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

    // The module decides from the amount, and the API shows the decision. The driver does not
    // know the limit, which is configuration of the module.
    if (!support.loanRequestOf(scenario).riskAssessmentNeeded()) {
      throw new ProcessStateNotReachedException(scenario, this, "the module does not ask for a risk assessment"
          + " for the amount "
          + scenario.getTestData().amount()
          + ". Use a larger amount in the test data, for example "
          + TestData.LARGE_AMOUNT
          + ".");
    }

    support.awaitLoanRequest(
        scenario,
        this,
        loanRequest -> loanRequest.assessRiskTaskId() != null,
        loanRequest -> Boolean.TRUE.equals(loanRequest.riskAssessed())
            ? "the risk is assessed already, so the workflow is past the task. Did somebody else complete it?"
            : "the workflow did not open the task. Look into the log of the application for an error"
                + " of the BPMS adapter.");

  }

}
