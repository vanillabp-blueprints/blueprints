package blueprint.testdriver.loanapproval.steps;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.simulator.SimulatorTestApi;
import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A customer requests a loan. The step reserves a customer at the simulated credit rating, gives
 * it the score of the test data, and requests the loan through the API of the module, as a
 * person in the browser does.
 */
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class LoanRequestedStep implements Step {

  @Inject
  SimulatorTestApi simulator;

  @Inject
  LoanApprovalApi loanApproval;

  @Inject
  StepSupport support;

  @Override
  public String description() {

    return "a customer requests a loan";

  }

  @Override
  public void run(
      final Scenario scenario) {

    // A customer of its own, so no other scenario changes its score.
    final var customerId = simulator.nextCustomerId();
    simulator.createRating(customerId, scenario.getTestData().creditScore());
    scenario.setCustomerId(customerId);

    final var answer = loanApproval.request(
        customerId,
        scenario.getTestData().customerName(),
        scenario.getTestData().amount());
    scenario.setLoanRequestId(answer.getLoanRequestId());
    if (!"started".equals(answer.getOutcome())) {
      throw new ProcessStateNotReachedException(scenario, this, "the module declined the loan request at once."
          + " The credit rating answered "
          + scenario.getTestData().creditScore()
          + ", which is too low. Use a higher credit score in the test data.");
    }

    support.awaitLoanRequest(
        scenario,
        this,
        loanRequest -> true,
        loanRequest -> "the workflow did not start");

  }

}
