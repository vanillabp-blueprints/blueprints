package blueprint.workflowmodule.ratewatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.ratewatch.model.AggregateRepository;

/**
 * The test of the second use case, and with it the test of what "per workflow module"
 * means: the loan approval broadcasts the signal, and this process catches it although the
 * two share no code at all.
 */
public class RateWatchIT extends WorkflowModuleTest {

  private static final BigDecimal RATE = new BigDecimal("3.5");

  @Autowired
  private Service rateWatches;

  @Autowired
  private AggregateRepository watches;

  @Autowired
  private blueprint.workflowmodule.loanapproval.Service loanApprovals;

  @Test
  @DisplayName("A signal broadcast by one use case reaches the other process of the module")
  public void theBroadcastReachesEveryProcessOfTheModule() {

    final var watchId = UUID.randomUUID().toString();
    rateWatches.startWatching(watchId);
    awaitAggregate(watches, watchId);

    // the loan approval use case sends it, and it knows nothing about rate watches
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          loanApprovals.publishInterestRate(RATE);
          return watches
              .findById(watchId)
              .filter(watch -> watch.getNoticedAt() != null)
              .isPresent();
        });

    final var watch = awaitAggregate(
        watches,
        watchId,
        aggregate -> aggregate.getNoticedAt() != null);

    assertThat(watch.getNoticedAt()).isNotNull();

  }

}
