package blueprint.testdriver.loanapproval;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.steps.Step;
import blueprint.testdriver.loanapproval.steps.StepSupport;
import lombok.extern.slf4j.Slf4j;

/**
 * Brings a real loan approval to a point of its process. It runs the steps of the
 * {@link ProcessState}, one after the other. Every step goes through the business code of the
 * workflow module and through the test API of the simulator, and waits for the aggregate. The
 * driver never talks to a BPMS, and it never jumps into the engine. A loan approval it brings
 * somewhere went the whole way, as a real one does.
 *
 * <p>
 * A test calls {@link #advanceTo(ProcessState, TestData)} and then checks the user task of that
 * state. The dev shell and the tests of a user interface call the same through the REST API of
 * {@link ProcessDriverController}.
 * </p>
 */
@Slf4j
@Component
public class ProcessDriver {

  private final Map<Class<? extends Step>, Step> steps;

  private final StepSupport support;

  ProcessDriver(
      final List<Step> steps,
      final StepSupport support) {

    this.steps = steps
        .stream()
        .collect(Collectors.toMap(Step::getClass, Function.identity()));
    this.support = support;

  }

  /**
   * Brings a new loan request with the default test data to the given state.
   *
   * @param state Where the loan approval should wait.
   * @return The loan request, once it reached the state.
   */
  public Scenario advanceTo(
      final ProcessState state) {

    return advanceTo(state, TestData.defaults());

  }

  /**
   * Brings a new loan request to the given state.
   *
   * @param state    Where the loan approval should wait.
   * @param testData What the scenario says about the loan request.
   * @return The loan request, once it reached the state.
   * @throws blueprint.testdriver.loanapproval.steps.ProcessStateNotReachedException If the state
   *                                                                                 is not
   *                                                                                 reached. The
   *                                                                                 message says
   *                                                                                 why.
   */
  public Scenario advanceTo(
      final ProcessState state,
      final TestData testData) {

    final var scenario = new Scenario(state, testData);

    for (final var stepClass : state.prerequisiteSteps()) {
      final var step = steps.get(stepClass);
      log.debug("{} on the way to {}: {}", scenario.getLoanRequestId(), state, step.description());
      step.run(scenario);
    }

    log.info(
        "Loan approval '{}' reached {}. Show it -> http://localhost:8080/api/loan-approval/{}",
        scenario.getLoanRequestId(),
        state,
        scenario.getLoanRequestId());

    return scenario;

  }

  /**
   * The id of the user task which is open in the state the scenario reached.
   *
   * @param scenario A loan request which reached its state.
   * @return The id of the open user task.
   */
  public String openUserTaskId(
      final Scenario scenario) {

    return scenario
        .getState()
        .openUserTaskId(support.loanApprovalOf(scenario));

  }

}
