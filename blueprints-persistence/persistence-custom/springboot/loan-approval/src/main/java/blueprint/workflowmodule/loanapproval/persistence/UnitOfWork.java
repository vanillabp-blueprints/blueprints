package blueprint.workflowmodule.loanapproval.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.vanillabp.integration.spi.TransactionRunner;

/**
 * The unit of work of this application, and the first of the four beans this blueprint is
 * about.
 *
 * <p>
 * VanillaBP brackets everything it does around a workflow aggregate in one transaction:
 * loading the aggregate, running the {@code @WorkflowTask} method, saving the aggregate,
 * recording the delivery and scheduling the second phase of a start either all commit or none
 * of them do. Which transaction that is, is a question the platform answers as long as the
 * platform manages the persistence. Here it does not, so the application answers it, and this
 * class is the answer.
 * </p>
 *
 * <p>
 * Resolution is worth knowing: VanillaBP takes a {@code TransactionRunnerAware} bean for a
 * specific aggregate first, this plain bean second, and the platform's own runner last. One
 * bean per application is the simple case and what this blueprint shows; an application holding
 * several persistences attributes each aggregate to its unit of work with the aware bean
 * instead. Booting names the winner per aggregate, which is the line to read when in doubt.
 * </p>
 *
 * <p>
 * In memory a unit of work is a counter and a list of things to do afterwards. In an
 * application it is whatever the store offers: the transaction of an event store, the
 * producer transaction of a message broker, the unit of work of a service behind an API. What
 * matters is the contract, and it is short: a {@code RuntimeException} out of the work rolls
 * back and propagates, a normal return commits, and implementations are called from several
 * threads at once.
 * </p>
 *
 * <p>
 * The counters exist for the test. They are what proves that VanillaBP really used this class
 * rather than something of the platform, which on a platform that has a transaction manager of
 * its own is not visible from the outcome alone.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Component
public class UnitOfWork implements TransactionRunner {

  private final AtomicInteger opened = new AtomicInteger();

  private final AtomicInteger committed = new AtomicInteger();

  private final AtomicInteger rolledBack = new AtomicInteger();

  /**
   * How deeply nested the work on this thread is. A unit of work is per thread, because
   * VanillaBP calls in on the threads of the BPMS adapters and on the one dispatching the
   * outbox.
   */
  private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

  /** What the stores asked to be done once the outermost work has committed. */
  private final ThreadLocal<List<Runnable>> afterCommit = ThreadLocal.withInitial(ArrayList::new);

  /** What they asked to be done if it does not commit. */
  private final ThreadLocal<List<Runnable>> afterRollback = ThreadLocal.withInitial(ArrayList::new);

  /**
   * @return How often a unit of work was opened.
   */
  public int getOpened() {

    return opened.get();

  }

  /**
   * @return How often one was committed.
   */
  public int getCommitted() {

    return committed.get();

  }

  /**
   * @return How often one was rolled back.
   */
  public int getRolledBack() {

    return rolledBack.get();

  }

  /** Forgets the counters, so a test can measure one interaction. */
  public void resetCounters() {

    opened.set(0);
    committed.set(0);
    rolledBack.set(0);

  }

  /**
   * Registers work to be done after the outermost unit of work has committed: this is how a
   * store of the application enlists. Everything it collected becomes visible here and
   * nowhere else, which is what makes "the aggregate is stored if and only if the unit of work
   * commits" true rather than a hope. The outbox uses the same hook to dispatch.
   *
   * @param work What to do after the commit.
   */
  public void afterCommit(
      final Runnable work) {

    afterCommit
        .get()
        .add(work);

  }

  /**
   * Registers work to be done when the unit of work does not commit: the other half of
   * enlisting, where a store throws away what it collected.
   *
   * @param work What to do after the rollback.
   */
  public void afterRollback(
      final Runnable work) {

    afterRollback
        .get()
        .add(work);

  }

  @Override
  public <T> T requireNew(
      final Supplier<T> work) {

    opened.incrementAndGet();
    depth.set(depth.get() + 1);
    try {
      final var result = work.get();
      depth.set(depth.get() - 1);
      committed.incrementAndGet();
      if (depth.get() == 0) {
        afterRollback
            .get()
            .clear();
        run(afterCommit);
      }
      return result;
    } catch (final RuntimeException failure) {
      depth.set(depth.get() - 1);
      rolledBack.incrementAndGet();
      if (depth.get() == 0) {
        afterCommit
            .get()
            .clear();
        run(afterRollback);
      }
      throw failure;
    }

  }

  private void run(
      final ThreadLocal<List<Runnable>> hooks) {

    final var pending = List.copyOf(hooks.get());
    hooks
        .get()
        .clear();
    pending.forEach(Runnable::run);

  }

  @Override
  public <T> T inCurrent(
      final Supplier<T> work) {

    if (depth.get() == 0) {
      throw new IllegalStateException(
          "No unit of work is open on this thread. VanillaBP asks for this only where an"
              + " embedded engine invokes a handler inside the engine's own transaction, and"
              + " this blueprint runs on a remote one.");
    }
    return work.get();

  }

  @Override
  public <T> T requireTransaction(
      final Supplier<T> work) {

    return depth.get() > 0
        ? work.get()
        : requireNew(work);

  }

  @Override
  public boolean isTransactionActive() {

    return depth.get() > 0;

  }

  @Override
  public boolean isRollbackOnly() {

    // Nothing can mark this unit of work as unable to commit - a platform's transaction
    // annotation has no effect on a store the platform does not manage.
    return false;

  }

}
