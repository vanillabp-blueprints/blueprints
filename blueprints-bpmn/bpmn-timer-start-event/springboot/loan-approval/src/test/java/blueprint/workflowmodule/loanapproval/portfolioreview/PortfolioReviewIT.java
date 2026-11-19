package blueprint.workflowmodule.loanapproval.portfolioreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.portfolioreview.model.Aggregate;
import blueprint.workflowmodule.loanapproval.portfolioreview.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.portfolioreview.model.Occasion;

/**
 * The test of a process with two start events which the BPMS fires on its own, each served by
 * its own {@code @WorkflowStartedByBpms(id = ...)} method.
 *
 * <p>
 * Nothing here calls {@code startWorkflow}, because nothing could. The test waits for an
 * aggregate to appear. The occasion on the aggregate shows which method built it, so it proves
 * that each start event reached its own method.
 * </p>
 */
public class PortfolioReviewIT extends WorkflowModuleTest {

  @Autowired
  private Service portfolioReview;

  @Autowired
  private AggregateRepository reviews;

  private Aggregate awaitReviewFor(
      final Occasion occasion,
      final Duration timeout) {

    final var found = new AtomicReference<Aggregate>();

    await()
        .atMost(timeout)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          reviews
              .findByOccasion(occasion)
              .stream()
              .findFirst()
              .ifPresent(found::set);
          return found.get() != null;
        });

    return found.get();

  }

  @Test
  @DisplayName("The timer starts a workflow, and the method for the timer start event builds its aggregate")
  public void theTimerReachesTheMethodOfItsStartEvent() {

    // Nothing is called here. The timer of the start event fires a few seconds after the
    // model was deployed. The scheduler of the engine fires it, so the test waits as long as
    // the harness allows for a scheduler.
    final var review = awaitReviewFor(Occasion.SCHEDULED, SCHEDULER_TIMEOUT);

    // the method for the timer start event built this aggregate, so it knows no signal
    assertThat(review.getReviewId()).isNotBlank();
    assertThat(review.getSignalName()).isNull();

    // from here on it is an ordinary workflow: the service task ran
    final var reviewed = awaitAggregate(
        reviews,
        review.getReviewId(),
        aggregate -> aggregate.getApprovalsReviewed() != null);
    assertThat(reviewed.getApprovalsReviewed()).isNotNegative();

  }

  @Test
  @DisplayName("A broadcast reaches the method for the signal start event, not the one for the timer")
  public void theSignalReachesTheMethodOfItsStartEvent() {

    // A signal is not buffered, so it is sent again until a workflow appeared. The
    // application asks, it does not start: what the broadcast does is the engine's business.
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          portfolioReview.request();
          return !reviews
              .findByOccasion(Occasion.REQUESTED)
              .isEmpty();
        });

    final var review = awaitReviewFor(Occasion.REQUESTED, TIMEOUT);

    // only the method for the signal start event writes the name of the signal
    assertThat(review.getSignalName()).isEqualTo(Workflow.REVIEW_REQUESTED);

    final var reviewed = awaitAggregate(
        reviews,
        review.getReviewId(),
        aggregate -> aggregate.getApprovalsReviewed() != null);
    assertThat(reviewed.getApprovalsReviewed()).isNotNegative();

  }

}
