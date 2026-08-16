package blueprint.workflowmodule.nightlyreview;

import java.util.List;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.nightlyreview.model.Aggregate;
import io.vanillabp.spi.service.BpmsStartTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the nightly review: look at the loan approvals that piled up.
 *
 * <p>
 * What is missing here is the point of this blueprint. There is no method starting a
 * workflow, because nobody starts one - the engine does, on its timer or on a broadcast
 * signal, and the application learns about it through
 * {@link WorkflowTaskHandler#reviewDue}.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  @Inject
  blueprint.workflowmodule.nightlyreview.model.AggregateRepository reviews;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  /**
   * The engine started a review. This runs in the transaction VanillaBP opened for the
   * start, and what it writes is saved with the aggregate.
   *
   * <p>
   * The trigger is the only thing telling the two start events apart afterwards: a timer
   * reports its scheduled time, a signal its name. Note what is NOT here: an ID. VanillaBP
   * assigned it before this method was called, from the most stable identity the BPMS
   * offers.
   * </p>
   *
   * @param review  The aggregate VanillaBP built.
   * @param trigger What made the BPMS start this workflow.
   */
  public void reviewDue(
      final Aggregate review,
      final BpmsStartTrigger trigger) {

    review.setStartedBy(trigger.kind().name());
    review.setTriggeredAt(trigger.time());
    review.setStartEventId(trigger.startEventId());

    log.info(
        "A nightly review '{}' was started by the BPMS: {} at {}, from start event '{}'."
            + " Nobody called startWorkflow",
        review.getReviewId(),
        trigger.kind(),
        trigger.time(),
        trigger.startEventId());

  }

  /**
   * Reviews the loan approvals, which is what the service task of the process triggers.
   *
   * @param review The workflow's aggregate.
   */
  public void reviewPendingApprovals(
      final Aggregate review) {

    final var reviewed = (int) loanApprovals.count();

    review.setApprovalsReviewed(reviewed);

    log.info(
        "The nightly review '{}' looked at {} loan approval(s)",
        review.getReviewId(),
        reviewed);

  }

  /**
   * Asks for a review right now instead of waiting for the timer.
   *
   * @see Workflow#reviewRequested()
   */
  @Transactional
  public void requestReview() {

    workflow.reviewRequested();

    log.info("A review was requested. Whether one starts is the engine's decision");

  }

  /**
   * The reviews that ran, newest first is not needed here - a blueprint shows them all.
   *
   * @return Every review this application has seen.
   */
  public List<Aggregate> getReviews() {

    return reviews.listAll();

  }

  /**
   * One review, if it exists.
   *
   * @param reviewId The ID VanillaBP assigned.
   * @return The review.
   */
  public Optional<Aggregate> getReview(
      final String reviewId) {

    return reviews.findByIdOptional(reviewId);

  }

}
