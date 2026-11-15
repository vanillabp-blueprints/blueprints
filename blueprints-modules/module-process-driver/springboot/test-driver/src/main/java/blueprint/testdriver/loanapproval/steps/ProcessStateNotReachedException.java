package blueprint.testdriver.loanapproval.steps;

import blueprint.testdriver.loanapproval.Scenario;

/**
 * A loan request did not reach the state a scenario asked for. The message names the step which
 * failed and says why, as far as the aggregate tells. A test which fails with it shows the reason
 * and not only a timeout.
 */
public class ProcessStateNotReachedException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  /**
   * Builds the message from the scenario, the step and the reason.
   *
   * @param scenario The loan request on its way.
   * @param step     The step which failed.
   * @param why      Why the state was not reached, in words of the business.
   */
  public ProcessStateNotReachedException(
      final Scenario scenario,
      final Step step,
      final String why) {

    super("Loan approval '"
        + scenario.getLoanRequestId()
        + "' did not reach "
        + scenario.getState()
        + " with "
        + scenario.getTestData()
        + ". The step '"
        + step.description()
        + "' failed: "
        + why);

  }

}
