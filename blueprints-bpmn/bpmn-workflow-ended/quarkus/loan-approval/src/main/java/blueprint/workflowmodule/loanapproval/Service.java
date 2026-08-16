package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.service.WorkflowEnd;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * {@link #loanApprovalClosed} is the interesting one. It is business code like every other
 * method here - it closes the case - and nothing in it says that a BPMS end event is what
 * triggered it.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the method the API calls, because
 * starting a workflow has to run in a transaction. It is deliberately absent from the
 * methods a task handler calls, and from the one the end notification calls: VanillaBP
 * already runs both in a transaction it owns.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

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
   * Rates a loan request and turns the rating into what the gateway asks about.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.ratingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    loanApproval.setRatingBand(rating >= properties.minimumRating()
        ? "acceptable"
        : "too-low");

    log.info(
        "Credit rating of loan approval '{}' is {}, which counts as '{}'",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval.getRatingBand());

  }

  /**
   * Tells the customer about the approval. This is real work in front of an end event, and
   * that is the only reason a task is modelled there.
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
   * The business case is closed. Called for EVERY end of the workflow, whichever end event
   * it reached and whether a task was modelled in front of it or not.
   *
   * @param loanApproval The workflow's aggregate.
   * @param end          How and when it ended, as far as the BPMS reports it.
   */
  public void loanApprovalClosed(
      final Aggregate loanApproval,
      final WorkflowEnd end) {

    loanApproval.setClosedAt(end.time());
    loanApproval.setClosedBy(end
        .kind()
        .name());
    loanApproval.setClosedAtEndEvent(end.endEventId());

    log.info(
        "Loan approval '{}' is closed ({} at {}, end event '{}')",
        loanApproval.getLoanRequestId(),
        end.kind(),
        end.time(),
        end.endEventId());

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

}
