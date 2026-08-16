package blueprint.workflowmodule.ratewatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.ratewatch.model.AggregateRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The test of the second use case, and with it the test of what "per workflow module"
 * means: the loan approval broadcasts the signal, and this process catches it although the
 * two share no code at all.
 */
@QuarkusTest
public class RateWatchIT extends WorkflowModuleTest {

  private static final BigDecimal RATE = new BigDecimal("3.5");

  @Inject
  Service rateWatches;

  @Inject
  AggregateRepository watches;

  @Inject
  blueprint.workflowmodule.loanapproval.Service loanApprovals;

  @Test
  @DisplayName("A signal broadcast by one use case reaches the other process of the module")
  public void theBroadcastReachesEveryProcessOfTheModule() {

    final var watchId = UUID.randomUUID().toString();
    rateWatches.startWatching(watchId);
    awaitAggregate(watches::findByIdOptional, watchId);

    // the loan approval use case sends it, and it knows nothing about rate watches
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          loanApprovals.publishInterestRate(RATE);
          // Awaitility polls on a thread of its own, which has neither a transaction nor a
          // request context - the same reason the harness reads an aggregate this way.
          return QuarkusTransaction
              .requiringNew()
              .call(() -> watches
                  .findByIdOptional(watchId)
                  .filter(watch -> watch.getNoticedAt() != null)
                  .isPresent());
        });

    final var watch = awaitAggregate(
        watches::findByIdOptional,
        watchId,
        aggregate -> aggregate.getNoticedAt() != null);

    assertThat(watch.getNoticedAt()).isNotNull();

  }

}
