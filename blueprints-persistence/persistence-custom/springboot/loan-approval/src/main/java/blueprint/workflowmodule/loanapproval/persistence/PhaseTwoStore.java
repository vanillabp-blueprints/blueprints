package blueprint.workflowmodule.loanapproval.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vanillabp.integration.spi.PhaseTwoCall;
import io.vanillabp.integration.spi.PhaseTwoOperationRegistry;
import io.vanillabp.integration.spi.PhaseTwoOutbox;

/**
 * The transaction outbox of this application, and the third of the four beans.
 *
 * <p>
 * Starting a workflow on a remote BPMS cannot be part of the application's unit of work: the
 * engine is somewhere else. So it happens in two phases. The first one stores the aggregate and
 * an entry saying "this workflow still has to be started"; the second one talks to the engine and
 * runs after the commit. That entry is what this store keeps, and the reason it exists is
 * atomicity: no aggregate without its workflow, and no workflow without its aggregate.
 * </p>
 *
 * <p>
 * Three rules make an outbox one, and they are the ones to keep when this is replaced by
 * something durable:
 * </p>
 *
 * <ul>
 * <li><strong>The entry rides the unit of work.</strong> It becomes visible if and only if that
 * unit of work commits, which is why the entries are collected per unit of work and handed over
 * in {@code afterCommit}.</li>
 * <li><strong>The idempotency key is unique.</strong> Scheduling the same operation twice is a
 * no-op answered with {@code false}, which is what makes one workflow per aggregate rather than
 * one per attempt.</li>
 * <li><strong>Dispatch happens after the commit</strong>, by handing the call back to VanillaBP.
 * The store keeps the name of the operation and never interprets it.</li>
 * </ul>
 *
 * <p>
 * What a durable store adds, and what a list cannot show: retrying a failed dispatch with a
 * backoff, dispatching entries again after a restart, marking dispatched entries as done rather
 * than deleting them so the deduplication window outlives the dispatch, and blocking an entry
 * which keeps failing so somebody looks at it. The wiki names all of them, and VanillaBP's own
 * stores implement them.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Spring-Boot-integration#configure-the-transaction-outbox">Configure
 *      the transaction outbox</a>
 */
@Component
public class PhaseTwoStore implements PhaseTwoOutbox {

  @Autowired
  private PhaseTwoOperationRegistry operations;

  @Autowired
  private UnitOfWork unitOfWork;

  /** What is scheduled. Written when a unit of work commits, never before. */
  private final List<PhaseTwoCall> scheduled = new CopyOnWriteArrayList<>();

  /** What the unit of work running on this thread has scheduled so far. */
  private final ThreadLocal<List<PhaseTwoCall>> uncommitted = ThreadLocal.withInitial(ArrayList::new);

  /**
   * @return Everything scheduled so far, which the test looks at to see that a rolled-back start
   *         left nothing behind.
   */
  public List<PhaseTwoCall> getScheduled() {

    return List.copyOf(scheduled);

  }

  @Override
  public boolean schedule(
      final PhaseTwoCall call) {

    final var idempotencyKey = call
        .idempotencyKey()
        .orElse(null);
    if ((idempotencyKey != null) && isAlreadyScheduled(idempotencyKey)) {
      return false;
    }

    if (uncommitted
        .get()
        .isEmpty()) {
      enlist();
    }
    uncommitted
        .get()
        .add(call);
    return true;

  }

  private boolean isAlreadyScheduled(
      final String idempotencyKey) {

    return scheduled
        .stream()
        .anyMatch(entry -> hasKey(entry, idempotencyKey)) || uncommitted
            .get()
            .stream()
            .anyMatch(entry -> hasKey(entry, idempotencyKey));

  }

  private static boolean hasKey(
      final PhaseTwoCall call,
      final String idempotencyKey) {

    return idempotencyKey.equals(
        call
            .idempotencyKey()
            .orElse(null));

  }

  /**
   * Keeps the entries of this unit of work and dispatches them when it commits, and forgets them
   * when it does not.
   */
  private void enlist() {

    unitOfWork.afterCommit(() -> {
      final var entries = List.copyOf(uncommitted.get());
      uncommitted
          .get()
          .clear();
      scheduled.addAll(entries);
      entries.forEach(this::dispatch);
    });
    unitOfWork.afterRollback(
        () -> uncommitted
            .get()
            .clear());

  }

  /**
   * Hands one entry back to VanillaBP. The registry answers what the name of an operation means,
   * and an unknown name is a real finding: an entry written by a newer version of the software,
   * or an extension which is no longer part of this application.
   *
   * @param call The call as it was scheduled.
   */
  private void dispatch(
      final PhaseTwoCall call) {

    operations
        .dispatchFor(call.operation())
        .orElseThrow(() -> new IllegalStateException(
            "No phase-two operation named '"
                + call.operation()
                + "' is registered, so this entry cannot be dispatched. Registered: "
                + operations.registeredNames()))
        .dispatch(call, false);

  }

}
