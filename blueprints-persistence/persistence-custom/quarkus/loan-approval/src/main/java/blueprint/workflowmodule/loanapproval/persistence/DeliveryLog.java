package blueprint.workflowmodule.loanapproval.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.vanillabp.integration.spi.TaskDelivery;
import io.vanillabp.integration.spi.TaskDeliveryLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What this application remembers about tasks the BPMS delivered, and the fourth of the four
 * beans.
 *
 * <p>
 * A remote BPMS may hand the same task over twice - after a timeout, after a restart, after a
 * lost answer. VanillaBP records what it processed and answers a repeated delivery from that
 * record instead of running the handler a second time. Without this store there is nothing to
 * answer from, and the application starts with a message saying so.
 * </p>
 *
 * <p>
 * The record is written inside the same unit of work as everything else the task did, which is
 * what makes it truthful: work which was rolled back must not leave a record behind, or the
 * repetition that would have fixed it is skipped. Here that comes for free, because
 * {@code UnitOfWork} rolls back a map operation by never having made it visible - a durable
 * store has to arrange it, and VanillaBP's own ones do.
 * </p>
 *
 * <p>
 * Keeping records forever is not the intention either. A durable store deletes them after a
 * retention period which outlives the deliveries a BPMS may repeat.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#what-vanillabp-remembers-about-delivered-tasks">What
 *      VanillaBP remembers about delivered tasks</a>
 */
@ApplicationScoped
public class DeliveryLog implements TaskDeliveryLog {

  @Inject
  UnitOfWork unitOfWork;

  /** What is recorded. Written when a unit of work commits, never before. */
  private final Map<String, TaskDelivery> deliveries = new ConcurrentHashMap<>();

  /** What the unit of work running on this thread has recorded so far. */
  private final ThreadLocal<Map<String, TaskDelivery>> uncommitted = ThreadLocal.withInitial(LinkedHashMap::new);

  /**
   * @return How many deliveries are recorded, which the test uses to see that the log was
   *         written at all.
   */
  public int size() {

    return deliveries.size();

  }

  @Override
  public Optional<TaskDelivery> recordedDelivery(
      final String deliveryKey) {

    return Optional.ofNullable(
        uncommitted
            .get()
            .getOrDefault(deliveryKey, deliveries.get(deliveryKey)));

  }

  @Override
  public boolean record(
      final TaskDelivery delivery) {

    if (!unitOfWork.isTransactionActive()) {
      return deliveries.putIfAbsent(delivery.deliveryKey(), delivery) == null;
    }

    if (recordedDelivery(delivery.deliveryKey()).isPresent()) {
      return false;
    }
    if (uncommitted
        .get()
        .isEmpty()) {
      enlist();
    }
    uncommitted
        .get()
        .put(delivery.deliveryKey(), delivery);
    return true;

  }

  /**
   * Makes the records of this unit of work visible when it commits, and forgets them when it
   * does not. A record of work which was rolled back is worse than no record at all: the
   * repetition which would have fixed it would be answered from the log and never run.
   */
  private void enlist() {

    unitOfWork.afterCommit(() -> {
      deliveries.putAll(uncommitted.get());
      uncommitted
          .get()
          .clear();
    });
    unitOfWork.afterRollback(
        () -> uncommitted
            .get()
            .clear());

  }

}
