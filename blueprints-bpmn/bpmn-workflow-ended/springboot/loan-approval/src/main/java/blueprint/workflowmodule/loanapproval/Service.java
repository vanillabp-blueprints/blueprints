package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.service.WorkflowEnd;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * {@link #closed} is the interesting one. It is business code like every other
 * method here - it closes the case - and nothing in it says that a BPMS end event is what
 * triggered it.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task or by the end notification already runs in the transaction
 * VanillaBP opened for it and joins it, but the annotation still shows that the method needs
 * one.
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
@EnableConfigurationProperties(LoanApprovalProperties.class)
@Transactional
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  public void request(
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
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    loanApproval.setRatingBand(rating >= properties.getMinimumRating()
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
  public void closed(
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
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
