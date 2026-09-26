package blueprint.workflowmodule.nightlyreview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.nightlyreview.model.Aggregate;
import io.vanillabp.spi.service.BpmsStartTrigger;
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
@org.springframework.stereotype.Service("nightlyReviewService")
public class Service {

  @Autowired
  private blueprint.workflowmodule.nightlyreview.model.AggregateRepository reviews;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  /**
   * The engine started a review, so the application builds its aggregate. This runs in the
   * transaction VanillaBP opened for the start, and what it returns is saved in that same
   * transaction.
   *
   * <p>
   * The ID is picked here, and picking it is the whole point: a workflow is named by its
   * aggregate, whoever started it. A generated one does here, because a review is not
   * about anything the BPMS could name.
   * </p>
   *
   * <p>
   * The trigger tells the two start events apart: a signal reports its name, and both
   * report the BPMN id of the event that fired. What it does NOT report is a time. No BPMS
   * hands a start listener the moment it scheduled the start for, so a time here would be
   * invented; where the moment matters, the model writes it into a process variable of its
   * own (see {@link #reviewPendingApprovals}).
   * </p>
   *
   * @param trigger What made the BPMS start this workflow.
   * @return The aggregate of the review.
   */
  public Aggregate reviewDue(
      final BpmsStartTrigger trigger) {

    final var review = Aggregate
        .builder()
        .reviewId(UUID.randomUUID().toString())
        .startedBy(trigger.kind().name())
        .startEventId(trigger.startEventId())
        .build();

    log.info(
        "A nightly review '{}' was started by the BPMS: {}, from start event '{}'."
            + " Nobody called startWorkflow",
        review.getReviewId(),
        trigger.kind(),
        trigger.startEventId());

    return review;

  }

  /**
   * Reviews the loan approvals, which is what the service task of the process triggers.
   *
   * @param review     The workflow's aggregate.
   * @param reviewedAt The moment the model wrote into a process variable of its own.
   */
  public void reviewPendingApprovals(
      final Aggregate review,
      final String reviewedAt) {

    final var reviewed = (int) loanApprovals.count();

    review.setApprovalsReviewed(reviewed);
    review.setReviewedAt(reviewedAt);

    log.info(
        "The nightly review '{}' looked at {} loan approval(s) at {}",
        review.getReviewId(),
        reviewed,
        reviewedAt);

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

    return reviews.findAll();

  }

  /**
   * One review, if it exists.
   *
   * @param reviewId The ID the application gave it.
   * @return The review.
   */
  public Optional<Aggregate> getReview(
      final String reviewId) {

    return reviews.findById(reviewId);

  }

}
