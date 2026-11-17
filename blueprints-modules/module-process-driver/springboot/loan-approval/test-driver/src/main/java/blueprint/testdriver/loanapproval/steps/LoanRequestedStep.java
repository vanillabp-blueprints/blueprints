package blueprint.testdriver.loanapproval.steps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import blueprint.testdriver.simulator.SimulatorTestApi;

/**
 * A customer requests a loan. The step reserves a customer at the simulated credit rating, gives
 * it the score of the test data, and requests the loan through the API of the module, as a
 * person in the browser does.
 */
@Component
public class LoanRequestedStep implements Step {

  @Autowired
  private SimulatorTestApi simulator;

  @Autowired
  private LoanApprovalApi loanApproval;

  @Autowired
  private StepSupport support;

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
    scenario.setLoanRequestId(answer.loanRequestId());
    if (!"started".equals(answer.outcome())) {
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
