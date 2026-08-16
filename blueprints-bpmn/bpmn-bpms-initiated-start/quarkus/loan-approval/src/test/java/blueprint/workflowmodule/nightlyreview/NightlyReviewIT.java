package blueprint.workflowmodule.nightlyreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.nightlyreview.model.Aggregate;
import blueprint.workflowmodule.nightlyreview.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The test of a workflow nobody started.
 *
 * <p>
 * It cannot begin the way every other integration test begins - there is no call that
 * starts this workflow. It waits for an aggregate to appear instead, which is the whole
 * assertion: the engine started a process on its own and VanillaBP built the aggregate for
 * it.
 * </p>
 */
@QuarkusTest
public class NightlyReviewIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository reviews;

  private Aggregate awaitReviewStartedBy(
      final String kind) {

    final var found = new java.util.concurrent.atomic.AtomicReference<Aggregate>();

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          final var candidates = reviews.findByStartedBy(kind);
          candidates.stream().findFirst().ifPresent(found::set);
          return found.get() != null;
        });

    return found.get();

  }

  @Test
  @DisplayName("The timer starts a workflow and VanillaBP builds its aggregate")
  public void theTimerStartsAWorkflowNobodyAskedFor() {

    // no service call above: the cycle of the start event fired after the model was
    // deployed, which is the only thing that happened
    final var review = awaitReviewStartedBy("TIMER");

    // the ID was assigned by VanillaBP, from the most stable identity the BPMS offers
    assertThat(review.getReviewId()).isNotBlank();
    assertThat(review.getTriggeredAt()).isNotNull();
    assertThat(review.getStartEventId()).isEqualTo("StartEvent_ScheduledReview");

    // and from there it is an ordinary workflow: the service task ran
    final var reviewed = awaitAggregate(
        reviews::findByIdOptional,
        review.getReviewId(),
        aggregate -> aggregate.getApprovalsReviewed() != null);
    assertThat(reviewed.getApprovalsReviewed()).isNotNegative();

  }

  @Test
  @DisplayName("A broadcast signal starts one as well")
  public void aBroadcastStartsAWorkflow() {

    // A signal is not buffered, so this is sent repeatedly until a workflow appeared. The
    // application is asking, not starting: what the broadcast does is the engine's business.
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          service.requestReview();
          return !reviews.findByStartedBy("SIGNAL").isEmpty();
        });

    final var review = awaitReviewStartedBy("SIGNAL");

    assertThat(review.getReviewId()).isNotBlank();
    assertThat(review.getStartEventId()).isEqualTo("StartEvent_ReviewRequested");

  }

}
