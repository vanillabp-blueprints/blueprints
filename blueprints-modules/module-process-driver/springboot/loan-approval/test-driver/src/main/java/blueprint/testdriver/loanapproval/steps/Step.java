package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.TestData;

/**
 * One step on the way to a state of the process. A step does one thing: it acts like a user
 * through the API of the user interface, prepares data in the simulator, or waits. Then it waits until the application shows the
 * result, so the next step starts from a known state.
 *
 * <p>
 * A step names only the steps which have to be done right before it, with
 * {@link #prerequisites()}. The driver follows these names back to the first step. So a new task
 * in the model is a change of two steps: the new one names its predecessor, and the step after
 * it names the new one. No other step and no state changes.
 * </p>
 */
public interface Step {

  /**
   * The steps which have to be done right before this one. Only the direct ones: the driver
   * finds the steps before them by itself, and it runs a step only once, even if two steps name
   * it.
   *
   * @return The classes of the steps. Empty for a step which starts a loan request.
   */
  default List<Class<? extends Step>> prerequisites() {

    return List.of();

  }

  /**
   * Changes the test data before the first step runs, if this step needs something of it. A step
   * which needs a signed contract, for example, chooses a customer who signs. Most steps need
   * nothing and keep the test data as it is.
   *
   * @param testData The test data, as the steps before in the list left it.
   * @return The test data this step can work with.
   */
  default TestData prepare(
      final TestData testData) {

    return testData;

  }

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
