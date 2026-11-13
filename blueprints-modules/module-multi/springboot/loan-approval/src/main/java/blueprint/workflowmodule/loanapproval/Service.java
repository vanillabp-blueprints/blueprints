package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.commons.CustomerDirectory;
import blueprint.commons.Money;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, {@code loanRequested} rather than "start the process", and that class
 * decides what this means for the BPMN. The other direction runs through
 * {@link WorkflowTaskHandler}, which calls the methods below when the process reaches a
 * task.
 * </p>
 *
 * <p>
 * Both directions meet here, and that is the point: this is the one class describing the
 * use case, and it does so without naming a single BPMN element.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 *
 * <p>
 * No method here throws a {@code TaskException}. One which does, so the process can react,
 * needs {@code @Transactional(noRollbackFor = TaskException.class)}: VanillaBP commits its
 * transaction for that exception, and a plain {@code @Transactional} would mark it for
 * rollback.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@Transactional
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * From the shared library, and the reason it exists: the other use case asks the same
   * system for the same thing, and neither of them should own the way there.
   */
  @Autowired
  private CustomerDirectory customers;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  public void request(
      final String loanRequestId,
      final String customerId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .customerId(customerId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info(
        "Loan approval '{}' started: {} asks for {}",
        loanRequestId,
        customers.nameOf(customerId),
        Money.euro(amount).formatted());

  }

  /**
   * Rates a loan request. A real application would ask a rating service here; what matters
   * for the blueprint is where this code sits: in the business service, not in the
   * {@code @WorkflowTask} method which happens to trigger it.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}, provided by '{}'",
        loanApproval.getLoanRequestId(),
        rating,
        properties.getRatingProvider());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
