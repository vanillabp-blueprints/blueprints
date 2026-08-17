package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.persistence.AggregateStore;
import blueprint.workflowmodule.loanapproval.persistence.UnitOfWork;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * Where the other blueprints put {@code @Transactional}, this one opens the unit of work
 * itself. An annotation of the platform would start a transaction the store of this
 * application is not part of, and VanillaBP would refuse the workflow start because the unit
 * of work it asks about is not open. So the method the API calls brackets its work with
 * {@link UnitOfWork}, and that bracket is what the aggregate and the outbox entry ride.
 * </p>
 *
 * <p>
 * The methods a task handler calls bracket nothing, exactly as everywhere else: VanillaBP has
 * already opened the unit of work for the task, and it commits it for a {@code TaskException}
 * on purpose so the process can react to what the handler wrote.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  @Inject
  AggregateStore loanApprovals;

  @Inject
  UnitOfWork unitOfWork;

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
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount) {

    unitOfWork.requireNew(() -> {

      final var loanApproval = Aggregate
          .builder()
          .loanRequestId(loanRequestId)
          .amount(amount)
          .build();

      workflow.loanRequested(loanApproval);

      log.info("Loan approval '{}' started", loanRequestId);

      return null;

    });

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
        properties.ratingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    // Reading a map needs no unit of work. A store which has one for reads too would be
    // wrapped here, the same way the method above does it.
    return loanApprovals.find(loanRequestId);

  }

}
