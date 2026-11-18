package blueprint.testdriver.loanapproval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import blueprint.testdriver.loanapproval.steps.Step;
import blueprint.testdriver.loanapproval.steps.StepSupport;
import io.quarkus.arc.All;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Brings a real loan approval to a point of its process. It runs the steps which finish the
 * {@link ProcessState}, and before them the steps they need, one after the other.
 *
 * <p>
 * The driver plays the users of the application, and the simulator plays the systems around it.
 * Every step acts through the API of the user interface of the workflow module, as a person in
 * the browser does, or through the test API of the simulator. Then it waits until the API of the
 * module shows the result. The driver never talks to a BPMS, and it never jumps into the engine.
 * It does not even know the Java code of the module. A loan approval it brings somewhere went the
 * whole way, as a real one does.
 * </p>
 *
 * <p>
 * A test calls {@link #advanceTo(ProcessState, TestData)} and then checks the user task of that
 * state. The dev shell and the tests of a user interface call the same through the REST API of
 * {@link ProcessDriverController}.
 * </p>
 *
 * <p>
 * Each step names only its direct prerequisites. The driver follows them depth first: the
 * prerequisites of a step run before the step, in the order the step names them, and a step
 * which two other steps need runs only once. It does this for every state when it is created,
 * so a missing step or a cycle stops the start of the application, and not a test in the middle.
 * That is why it says {@code @Startup}: Quarkus would make it only when it is first used.
 * </p>
 */
@Slf4j
@Startup
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class ProcessDriver {

  private final Map<ProcessState, List<Step>> stepsOfState = new EnumMap<>(ProcessState.class);

  private final StepSupport support;

  @Inject
  ProcessDriver(
      @All final List<Step> steps,
      final StepSupport support) {

    final var stepByClass = steps
        .stream()
        .collect(Collectors.toMap(Step::getClass, Function.identity()));
    for (final var state : ProcessState.values()) {
      stepsOfState.put(state, resolve(state.finishingSteps(), stepByClass));
    }
    this.support = support;

  }

  /**
   * The steps the driver runs for a state, in their order.
   *
   * @param state A state of the process.
   * @return The steps, each one once.
   */
  List<Step> stepsOf(
      final ProcessState state) {

    return stepsOfState.get(state);

  }

  /**
   * Puts the given steps and all steps they need into the order in which they have to run.
   *
   * @param targets     The steps which have to be done at the end.
   * @param stepByClass The instance of each step.
   * @return Every needed step once, each one after its prerequisites.
   * @throws IllegalStateException If a step has no instance, or if steps need each other in a
   *                               cycle.
   */
  static List<Step> resolve(
      final Collection<Class<? extends Step>> targets,
      final Map<Class<? extends Step>, Step> stepByClass) {

    final var ordered = new LinkedHashSet<Step>();
    for (final var target : targets) {
      visit(target, stepByClass, new LinkedHashSet<>(), ordered);
    }
    return List.copyOf(ordered);

  }

  private static void visit(
      final Class<? extends Step> stepClass,
      final Map<Class<? extends Step>, Step> stepByClass,
      final LinkedHashSet<Class<? extends Step>> onTheWay,
      final LinkedHashSet<Step> ordered) {

    final var step = stepByClass.get(stepClass);
    if (step == null) {
      throw new IllegalStateException("There is no bean of the step "
          + stepClass.getSimpleName()
          + ". Annotate it like the other steps, with @Singleton and @IfBuildProperty.");
    }
    if (ordered.contains(step)) {
      return;
    }
    // The steps we are inside of right now. Meeting one of them again means a cycle, which
    // would otherwise end in a StackOverflowError without a hint which steps are wrong.
    if (!onTheWay.add(stepClass)) {
      final var cycle = new ArrayList<String>();
      var inCycle = false;
      for (final var before : onTheWay) {
        inCycle |= before.equals(stepClass);
        if (inCycle) {
          cycle.add(before.getSimpleName());
        }
      }
      cycle.add(stepClass.getSimpleName());
      throw new IllegalStateException("The prerequisites of the steps form a cycle: "
          + String.join(" -> ", cycle)
          + ". Remove one of these names from prerequisites().");
    }
    for (final var prerequisite : step.prerequisites()) {
      visit(prerequisite, stepByClass, onTheWay, ordered);
    }
    onTheWay.remove(stepClass);
    ordered.add(step);

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

    var preparedTestData = testData;
    for (final var step : stepsOfState.get(state)) {
      preparedTestData = step.prepare(preparedTestData);
    }
    final var scenario = new Scenario(state, preparedTestData);

    for (final var step : stepsOfState.get(state)) {
      log.debug("{} on the way to {}: {}", scenario.getLoanRequestId(), state, step.description());
      step.run(scenario);
    }

    log.info(
        "Loan approval '{}' reached {}. Show it -> http://localhost:8080/loan-approval/api/{}",
        scenario.getLoanRequestId(),
        state,
        scenario.getLoanRequestId());

    return scenario;

  }

  /**
   * The id of the user task which is open in the state the scenario reached.
   *
   * @param scenario A loan request which reached its state.
   * @return The id of the open user task, or {@code null} in a state where the workflow ended.
   */
  public String openUserTaskId(
      final Scenario scenario) {

    return scenario
        .getState()
        .openUserTaskId(support.loanRequestOf(scenario));

  }

}
