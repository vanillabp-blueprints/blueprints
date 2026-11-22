package blueprint.testdriver.loanapproval;

import java.util.List;
import java.util.function.Function;

import blueprint.testdriver.loanapproval.steps.ContractCheckOpenedStep;
import blueprint.testdriver.loanapproval.steps.ContractDeclinedStep;
import blueprint.testdriver.loanapproval.steps.ContractSignedStep;
import blueprint.testdriver.loanapproval.steps.RiskAssessmentOpenedStep;
import blueprint.testdriver.loanapproval.steps.Step;
import blueprint.workflowmodule.loanapproval.api.client.model.LoanRequest;

/**
 * The points of the process a scenario can bring a loan approval to. Each one is a point where
 * the workflow waits, together with what happened before. Two states may name the same task of
 * the model with another history: "Check contract" with and without the signature of the
 * customer.
 *
 * <p>
 * A state ending in {@code _USER_TASK} means that the task is open. The driver does not complete
 * it. That is what the test does, or a person in the dev shell. A state without this ending is
 * one where the workflow ended, and no task is open.
 * </p>
 *
 * <p>
 * Each state names only the step which finishes it. Every step names the steps right before
 * it, and the driver follows them back to the start. So the way to a state is written down in
 * small pieces, each in the step it belongs to. The steps and this enum are the only place where
 * the tests know the process. A change of the model changes one or two steps, and no test.
 * </p>
 */
public enum ProcessState {

  /**
   * The user task "Assess risk" is open. Only a loan request of at least the limit of the
   * module gets there, so the test data has to name such an amount.
   */
  ASSESS_RISK_USER_TASK(LoanRequest::getAssessRiskTaskId),

  /**
   * The user task "Check contract" is open, and the signature of the customer has not come
   * back yet. The task cannot be completed in this state. With the default customer, a test
   * makes the simulator sign whenever it wants.
   */
  CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE(LoanRequest::getCheckContractTaskId),

  /**
   * The user task "Check contract" is open, and the customer signed. For a large amount the
   * driver assessed the risk on the way. The customer is always {@link TestData#JOHN_SIGNING},
   * who signs by himself.
   */
  CHECK_CONTRACT_USER_TASK(LoanRequest::getCheckContractTaskId),

  /**
   * The customer declined to sign the contract, and the workflow ended with "Loan declined". The
   * task "Check contract" was taken away, so no task is open. The customer is always
   * {@link TestData#JACK_NOSIGN}, who declines by himself.
   */
  CONTRACT_DECLINED(loanRequest -> null);

  private final Function<LoanRequest, String> openUserTaskId;

  ProcessState(
      final Function<LoanRequest, String> openUserTaskId) {

    this.openUserTaskId = openUserTaskId;

  }

  /**
   * The steps which finish this state, normally one. The driver runs the steps they need before
   * them, see {@link Step#prerequisites()}.
   *
   * @return The classes of the steps. The driver knows one instance of each.
   */
  public List<Class<? extends Step>> finishingSteps() {

    return switch (this) {
      case ASSESS_RISK_USER_TASK -> List.of(RiskAssessmentOpenedStep.class);
      case CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE -> List.of(ContractCheckOpenedStep.class);
      case CHECK_CONTRACT_USER_TASK -> List.of(ContractSignedStep.class);
      case CONTRACT_DECLINED -> List.of(ContractDeclinedStep.class);
    };

  }

  /**
   * The id of the user task which is open in this state.
   *
   * @param loanRequest A loan request in this state, as the API of the module shows it.
   * @return The id of the open user task, or {@code null} in a state where the workflow ended.
   */
  public String openUserTaskId(
      final LoanRequest loanRequest) {

    return openUserTaskId.apply(loanRequest);

  }

}
