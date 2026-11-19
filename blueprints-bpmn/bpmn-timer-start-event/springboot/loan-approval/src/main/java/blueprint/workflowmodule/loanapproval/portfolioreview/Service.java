package blueprint.workflowmodule.loanapproval.portfolioreview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.portfolioreview.model.Aggregate;
import blueprint.workflowmodule.loanapproval.portfolioreview.model.Occasion;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the portfolio review: look at the loan approvals which piled up.
 *
 * <p>
 * There is no method which starts a review. The engine starts one, on its timer or on a
 * broadcast signal. The application learns about it through one of the two
 * {@code @WorkflowStartedByBpms} methods of {@link WorkflowTaskHandler}, and each of them calls
 * its own method here.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method called
 * by VanillaBP already runs in the transaction VanillaBP opened for it and joins it, but the
 * annotation still shows that the method needs one.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service("portfolioReviewService")
@Transactional
public class Service {

  @Autowired
  private blueprint.workflowmodule.loanapproval.portfolioreview.model.AggregateRepository reviews;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  /**
   * The timer fired, so a scheduled review begins. This builds its aggregate in the
   * transaction VanillaBP opened for the start, and VanillaBP saves what it returns.
   *
   * @return The aggregate of a scheduled review.
   */
  public Aggregate scheduled() {

    final var review = Aggregate
        .builder()
        .reviewId(UUID.randomUUID().toString())
        .occasion(Occasion.SCHEDULED)
        .build();

    log.info(
        "The timer started the portfolio review '{}'. Nobody called startWorkflow",
        review.getReviewId());

    return review;

  }

  /**
   * Somebody asked for a review, and the engine started one for the broadcast. This builds a
   * different aggregate than {@link #scheduled()}. It says so, and it keeps the name of the
   * signal.
   *
   * @param signalName The name of the signal which was broadcast.
   * @return The aggregate of a review somebody asked for.
   */
  public Aggregate requested(
      final String signalName) {

    final var review = Aggregate
        .builder()
        .reviewId(UUID.randomUUID().toString())
        .occasion(Occasion.REQUESTED)
        .signalName(signalName)
        .build();

    log.info(
        "The signal '{}' started the portfolio review '{}'. Nobody called startWorkflow",
        signalName,
        review.getReviewId());

    return review;

  }

  /**
   * Reviews the loan approvals, which is what the service task of the process triggers.
   *
   * @param review The workflow's aggregate.
   */
  public void reviewPortfolio(
      final Aggregate review) {

    final var reviewed = (int) loanApprovals.count();

    review.setApprovalsReviewed(reviewed);

    log.info(
        "The {} portfolio review '{}' looked at {} loan approval(s)",
        review.getOccasion(),
        review.getReviewId(),
        reviewed);

  }

  /**
   * Asks for a review right now instead of waiting for the timer.
   *
   * @see Workflow#reviewRequested()
   */
  public void request() {

    workflow.reviewRequested();

    log.info("A review was requested. Whether one starts is the engine's decision");

  }

  /**
   * The reviews which ran, whoever started them.
   *
   * @return Every review this application has seen.
   */
  public List<Aggregate> getAll() {

    return reviews.findAll();

  }

  /**
   * One review, if it exists.
   *
   * @param reviewId The ID the application gave it.
   * @return The review.
   */
  public Optional<Aggregate> get(
      final String reviewId) {

    return reviews.findById(reviewId);

  }

}
