package blueprint.testdriver.loanapproval.steps;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.simulator.SimulatorTestApi;
import blueprint.workflowmodule.loanapproval.Service;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;

/**
 * A customer requests a loan. The step reserves a customer at the simulated credit rating, gives
 * it the score of the test data, and requests the loan through the business code, as the API of
 * the module does.
 */
@Component
public class LoanRequestedStep implements Step {

  @Autowired
  private SimulatorTestApi simulator;

  @Autowired
  private Service loanApproval;

  @Autowired
  private LoanApprovalProperties properties;

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
    scenario.setLoanRequestId(UUID
        .randomUUID()
        .toString());

    final var started = new AtomicBoolean();
    support.inOwnTransaction(() -> started.set(loanApproval.request(
        scenario.getLoanRequestId(),
        customerId,
        scenario.getTestData().amount())));
    if (!started.get()) {
      throw new ProcessStateNotReachedException(scenario, this, "the loan request was declined at once. The"
          + " credit rating answered "
          + scenario.getTestData().creditScore()
          + ", and the module declines a score below "
          + properties.getMinimumCreditScore()
          + ". Use a higher credit score in the test data.");
    }

    support.awaitLoanApproval(
        scenario,
        this,
        loanRequest -> true,
        loanRequest -> "the workflow did not start");

  }

}
