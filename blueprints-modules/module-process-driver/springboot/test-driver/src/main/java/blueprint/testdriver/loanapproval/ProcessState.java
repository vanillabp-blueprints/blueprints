package blueprint.testdriver.loanapproval;

import java.util.List;
import java.util.function.Function;

import blueprint.testdriver.loanapproval.steps.ContractCheckOpenedStep;
import blueprint.testdriver.loanapproval.steps.ContractSignedStep;
import blueprint.testdriver.loanapproval.steps.LoanRequestedStep;
import blueprint.testdriver.loanapproval.steps.RiskAssessedOnTheWayStep;
import blueprint.testdriver.loanapproval.steps.RiskAssessmentOpenedStep;
import blueprint.testdriver.loanapproval.steps.Step;
import blueprint.workflowmodule.loanapproval.model.Aggregate;

/**
 * The points of the process a scenario can bring a loan approval to. Each one is a point where
 * the workflow waits, together with what happened before. Two states may name the same task of
 * the model with another history: "Check contract" with and without the signature of the
 * customer.
 *
 * <p>
 * A state ending in {@code _USER_TASK} means that the task is open. The driver does not complete
 * it. That is what the test does, or a person in the dev shell.
 * </p>
 *
 * <p>
 * Each state lists the steps which lead to it, in their order. This list is the only place
 * where the tests know the process. A change of the model changes a step or a list here, and
 * no test.
 * </p>
 */
public enum ProcessState {

  /**
   * The user task "Assess risk" is open. Only a loan request of at least the limit of the
   * module gets there, so the test data has to name such an amount.
   */
  ASSESS_RISK_USER_TASK(Aggregate::getAssessRiskTaskId),

  /**
   * The user task "Check contract" is open, and the signature of the customer has not come
   * back yet. The task cannot be completed in this state.
   */
  CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE(Aggregate::getCheckContractTaskId),

  /**
   * The user task "Check contract" is open, and the customer signed. For a large amount the
   * driver assessed the risk on the way.
   */
  CHECK_CONTRACT_USER_TASK(Aggregate::getCheckContractTaskId);

  private final Function<Aggregate, String> openUserTaskId;

  ProcessState(
      final Function<Aggregate, String> openUserTaskId) {

    this.openUserTaskId = openUserTaskId;

  }

  /**
   * The steps which lead to this state, in their order.
   *
   * @return The classes of the steps. The driver knows one instance of each.
   */
  public List<Class<? extends Step>> prerequisiteSteps() {

    return switch (this) {
      case ASSESS_RISK_USER_TASK -> List.of(
          LoanRequestedStep.class,
          RiskAssessmentOpenedStep.class);
      case CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE -> List.of(
          LoanRequestedStep.class,
          RiskAssessedOnTheWayStep.class,
          ContractCheckOpenedStep.class);
      case CHECK_CONTRACT_USER_TASK -> List.of(
          LoanRequestedStep.class,
          RiskAssessedOnTheWayStep.class,
          ContractCheckOpenedStep.class,
          ContractSignedStep.class);
    };

  }

  /**
   * The id of the user task which is open in this state.
   *
   * @param loanApproval The aggregate of a loan approval in this state.
   * @return The id of the open user task.
   */
  public String openUserTaskId(
      final Aggregate loanApproval) {

    return openUserTaskId.apply(loanApproval);

  }

}
