package blueprint.workflowmodule.loanapproval;

import java.time.Duration;

import blueprint.workflowmodule.Simulator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * The document service, as far as the test is concerned, plus the one thing that makes this
 * blueprint testable: it can be slow on purpose.
 *
 * <p>
 * The collision this blueprint is about needs both branches inside their transactions at the
 * same time. Waiting for that to happen by chance is a test which passes nineteen times out
 * of twenty and fails on the CI machine. So the test makes this call take a moment, answers
 * the other branch while it lasts, and asserts afterwards that both results are there.
 * </p>
 *
 * <p>
 * The delay is bounded rather than a latch the test opens: a task handler holds a thread of
 * the BPMS, and on a remote engine that thread is shared by everything the adapter does. A
 * handler which blocks until somebody lets it go stalls the whole adapter, which is worth
 * knowing before writing one.
 * </p>
 *
 * @see Simulator
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class DocumentsSimulator extends Simulator implements DocumentsClient {

  private volatile Duration takesAtLeast = Duration.ZERO;

  /**
   * Makes the next call take at least this long, which is the window the test answers the
   * other branch in.
   *
   * @param duration How long the document service takes.
   */
  public void takesAtLeast(
      final Duration duration) {

    takesAtLeast = duration;

  }

  @Override
  public int collect(
      final String loanRequestId) {

    record("collect "
        + loanRequestId);

    final var duration = takesAtLeast;
    if (!duration.isZero()) {
      try {
        Thread.sleep(duration.toMillis());
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
    takesAtLeast = Duration.ZERO;

  }

}
