package blueprint.workflowmodule.loanapproval;

import java.util.List;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.Check;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, {@code checksSelected} rather than "complete the user task", and that class
 * decides what this means for the BPMN. The other direction runs through
 * {@link WorkflowTaskHandler}, which calls the methods below when the process reaches a
 * task.
 * </p>
 *
 * <p>
 * The methods running the checks are what the ad-hoc subprocess activates. Nothing here
 * knows that they sit inside such an element, and nothing here decides which of them run:
 * {@link #selectChecks} writes the picked ids onto the workflow aggregate, and the model
 * reads them.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow and answering a user task have to run in a transaction. It is
 * deliberately absent from the methods a task handler calls: VanillaBP already runs a task
 * in a transaction it owns, and a transaction declared here would break the guarantees that
 * come with it. VanillaBP sees such a transaction and fails the task naming it, so the
 * mistake shows up rather than costing data.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  /**
   * What a case worker may pick, which is every combination of at least one check. The
   * handler logs one URL per entry, so the choice can be made in a browser.
   */
  private static final List<List<Check>> SELECTABLE_COMBINATIONS = List.of(
      List.of(Check.INCOME),
      List.of(Check.FRAUD),
      List.of(Check.COLLATERAL),
      List.of(Check.INCOME, Check.FRAUD),
      List.of(Check.INCOME, Check.COLLATERAL),
      List.of(Check.FRAUD, Check.COLLATERAL),
      List.of(Check.INCOME, Check.FRAUD, Check.COLLATERAL));

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Rates a loan request, which is what the service task ahead of the selection triggers.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.ratingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * A case worker has to pick the additional checks: the process created the user task and
   * reports its id. Keeping that id is the entire job here, because it is the only way back
   * to this task.
   *
   * <p>
   * A real application would put the task where the people who have to act find it. This
   * blueprint logs one URL per combination, so the choice can be made in a browser.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task just created.
   */
  public void checkSelectionOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setSelectChecksTaskId(taskId);

    final var urls = new StringBuilder();
    SELECTABLE_COMBINATIONS
        .forEach(combination -> urls
            .append("\n  ")
            .append(padded(combination))
            .append(" -> http://localhost:8080/api/loan-approval/")
            .append(loanApproval.getLoanRequestId())
            .append("/select-checks/")
            .append(taskId)
            .append("?checks=")
            .append(names(combination)));

    log.info(
        "Loan approval '{}' waits for somebody to pick the additional checks. Continue with one of:{}",
        loanApproval.getLoanRequestId(),
        urls);

  }

  /**
   * The selection is gone without having been answered: the workflow took the user task
   * away. The stored id is dropped, because it does not lead anywhere any more.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkSelectionClosed(
      final Aggregate loanApproval) {

    loanApproval.setSelectChecksTaskId(null);

    log.info(
        "The selection of checks of loan approval '{}' was canceled",
        loanApproval.getLoanRequestId());

  }

  /**
   * The case worker picked. This is the answer the process waits for, and it is the whole
   * mechanism of the ad-hoc subprocess: the picked element ids go onto the workflow
   * aggregate, VanillaBP shares the aggregate with the BPMS while completing the task, and
   * the expression of the model turns that list into the activities to run.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task being answered.
   * @param checks        The checks to run, at least one.
   */
  @Transactional
  public void selectChecks(
      final String loanRequestId,
      final String taskId,
      final List<Check> checks) {

    if (checks.isEmpty()) {

      throw new IllegalArgumentException("Pick at least one check for loan approval '"
          + loanRequestId
          + "'");

    }

    final var loanApproval = openCheckSelection(loanRequestId, taskId);

    loanApproval.setChecksToRun(Check.elementIdsOf(checks));

    workflow.checksSelected(loanApproval, taskId);

    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setSelectChecksTaskId(null);

    log.info(
        "Loan approval '{}' runs the checks {}",
        loanRequestId,
        names(checks));

  }

  /**
   * Looks at the income of the applicant.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkIncome(
      final Aggregate loanApproval) {

    loanApproval.setIncomeChecked(true);

    log.info("Income of loan approval '{}' was checked", loanApproval.getLoanRequestId());

  }

  /**
   * Looks for fraud.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkFraud(
      final Aggregate loanApproval) {

    loanApproval.setFraudChecked(true);

    log.info("Loan approval '{}' was checked for fraud", loanApproval.getLoanRequestId());

  }

  /**
   * Looks at the collateral offered.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkCollateral(
      final Aggregate loanApproval) {

    loanApproval.setCollateralChecked(true);

    log.info("Collateral of loan approval '{}' was checked", loanApproval.getLoanRequestId());

  }

  /**
   * Tells the customer how their request ended, which is what the service task behind the
   * ad-hoc subprocess triggers. Reaching it means every activated check is done.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void informCustomer(
      final Aggregate loanApproval) {

    loanApproval.setCustomerInformed(true);

    log.info(
        "The customer of loan approval '{}' was informed",
        loanApproval.getLoanRequestId());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

  /**
   * The loan approval whose selection of checks is the given task, refusing anything else. A
   * task id is a URL somebody keeps, so it outlives the task it points at: the same link
   * opened twice has to be rejected here rather than being sent to the BPMS.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task expected to be open.
   * @return The loan approval.
   */
  private Aggregate openCheckSelection(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (!taskId.equals(loanApproval.getSelectChecksTaskId())) {

      throw new IllegalStateException("The selection of checks '"
          + taskId
          + "' of loan approval '"
          + loanRequestId
          + "' is not open any more");

    }

    return loanApproval;

  }

  private static String names(
      final List<Check> checks) {

    return String.join(
        ",",
        checks
            .stream()
            .map(check -> check.name().toLowerCase())
            .toList());

  }

  private static String padded(
      final List<Check> checks) {

    return "%-25s".formatted(names(checks));

  }

}
