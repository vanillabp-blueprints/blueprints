package blueprint.workflowmodule.loanapproval;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import blueprint.workflowmodule.Simulator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * The document service, as far as the test is concerned, plus the one thing that makes this
 * blueprint testable: it can be held.
 *
 * <p>
 * The collision this blueprint is about needs both branches inside their transactions at
 * the same time. Waiting for that to happen by chance is a test which passes nineteen times
 * out of twenty and fails on the CI machine. So the test holds this branch here, answers the
 * other one, and only then lets go.
 * </p>
 *
 * @see Simulator
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class DocumentsSimulator extends Simulator implements DocumentsClient {

  /** How long a held branch waits before it gives up, so a broken test fails rather than hangs. */
  private static final long TIMEOUT_SECONDS = 30;

  private volatile CountDownLatch held;

  /** Holds the next call until {@link #letGo()}. */
  public void hold() {

    held = new CountDownLatch(1);

  }

  /** Lets the held call finish. */
  public void letGo() {

    final var latch = held;
    if (latch != null) {
      latch.countDown();
    }

  }

  @Override
  public int collect(
      final String loanRequestId) {

    record("collect "
        + loanRequestId);

    final var latch = held;
    if (latch != null) {
      try {
        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          throw new IllegalStateException(
              "The test held the document service of loan approval '"
                  + loanRequestId
                  + "' and never let go");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    return 3;

  }

  @Override
  public void reset() {

    super.reset();
    held = null;

  }

}
