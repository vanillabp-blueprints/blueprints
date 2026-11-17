package blueprint.testdriver.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import blueprint.testdriver.loanapproval.steps.ContractCheckOpenedStep;
import blueprint.testdriver.loanapproval.steps.ContractDeclinedStep;
import blueprint.testdriver.loanapproval.steps.ContractSignedStep;
import blueprint.testdriver.loanapproval.steps.LoanRequestedStep;
import blueprint.testdriver.loanapproval.steps.RiskAssessedOnTheWayStep;
import blueprint.testdriver.loanapproval.steps.RiskAssessmentOpenedStep;
import blueprint.testdriver.loanapproval.steps.Step;

/**
 * Each step names only its direct prerequisites, and the driver finds the whole way from them.
 * These tests need no application, because the order depends only on the names.
 */
public class StepOrderTest {

  @Test
  public void everyStateGetsTheWholeWayFromTheStart() {

    final var driver = new ProcessDriver(
        List.of(
            new ContractSignedStep(),
            new ContractDeclinedStep(),
            new ContractCheckOpenedStep(),
            new RiskAssessedOnTheWayStep(),
            new RiskAssessmentOpenedStep(),
            new LoanRequestedStep()), null);

    assertThat(classesOf(driver.stepsOf(ProcessState.ASSESS_RISK_USER_TASK)))
        .containsExactly(LoanRequestedStep.class, RiskAssessmentOpenedStep.class);
    assertThat(classesOf(driver.stepsOf(ProcessState.CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE)))
        .containsExactly(LoanRequestedStep.class, RiskAssessedOnTheWayStep.class, ContractCheckOpenedStep.class);
    assertThat(classesOf(driver.stepsOf(ProcessState.CHECK_CONTRACT_USER_TASK)))
        .containsExactly(
            LoanRequestedStep.class,
            RiskAssessedOnTheWayStep.class,
            ContractCheckOpenedStep.class,
            ContractSignedStep.class);
    assertThat(classesOf(driver.stepsOf(ProcessState.CONTRACT_DECLINED)))
        .containsExactly(LoanRequestedStep.class, RiskAssessedOnTheWayStep.class, ContractDeclinedStep.class);

  }

  @Test
  public void aStepWhichTwoStepsNeedRunsOnce() {

    // Start <- Left, Start <- Right, Left and Right <- End: Start is reached on two ways.
    final var order = ProcessDriver.resolve(
        List.of(End.class),
        byClass(new Start(), new Left(), new Right(), new End()));

    assertThat(classesOf(order)).containsExactly(Start.class, Left.class, Right.class, End.class);

  }

  @Test
  public void thePrerequisitesRunInTheOrderTheStepNamesThem() {

    final var order = ProcessDriver.resolve(
        List.of(EndRightFirst.class),
        byClass(new Start(), new Left(), new Right(), new EndRightFirst()));

    assertThat(classesOf(order)).containsExactly(Start.class, Right.class, Left.class, EndRightFirst.class);

  }

  @Test
  public void aCycleNamesItsSteps() {

    assertThatThrownBy(() -> ProcessDriver.resolve(
        List.of(Third.class),
        byClass(new First(), new Second(), new Third())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("First -> Second -> First");

  }

  @Test
  public void aStepWithoutBeanIsNamed() {

    assertThatThrownBy(() -> ProcessDriver.resolve(
        List.of(Left.class),
        byClass(new Left())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Start");

  }

  private static List<Class<?>> classesOf(
      final List<Step> steps) {

    return steps
        .stream()
        .<Class<?>>map(Step::getClass)
        .toList();

  }

  private static Map<Class<? extends Step>, Step> byClass(
      final Step... steps) {

    return Stream
        .of(steps)
        .collect(Collectors.toMap(Step::getClass, Function.identity()));

  }

  private abstract static class TestStep implements Step {

    @Override
    public String description() {

      return getClass().getSimpleName();

    }

    @Override
    public void run(
        final Scenario scenario) {

      // The order is under test, not what a step does.

    }

  }

  private static class Start extends TestStep {
  }

  private static class Left extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(Start.class);

    }

  }

  private static class Right extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(Start.class);

    }

  }

  private static class End extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(Left.class, Right.class);

    }

  }

  private static class EndRightFirst extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(Right.class, Left.class);

    }

  }

  private static class First extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(Second.class);

    }

  }

  private static class Second extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(First.class);

    }

  }

  private static class Third extends TestStep {

    @Override
    public List<Class<? extends Step>> prerequisites() {

      return List.of(First.class);

    }

  }

}
