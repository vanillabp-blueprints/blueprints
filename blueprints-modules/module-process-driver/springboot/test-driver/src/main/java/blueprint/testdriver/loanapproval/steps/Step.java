package blueprint.testdriver.loanapproval.steps;

import blueprint.testdriver.loanapproval.Scenario;

/**
 * One step on the way to a state of the process. A step does one thing: it prepares data in the
 * simulator, triggers something, or waits. Then it waits until the application shows the
 * result, so the next step starts from a known state.
 */
public interface Step {

  /**
   * What the step does, for the log and for the message when a state is not reached.
   *
   * @return A short description.
   */
  String description();

  /**
   * Runs the step for one loan request.
   *
   * @param scenario The loan request on its way. The first step fills in its ids.
   * @throws ProcessStateNotReachedException If the application does not get where the step
   *                                         expects it. The message says why.
   */
  void run(
      Scenario scenario);

}
