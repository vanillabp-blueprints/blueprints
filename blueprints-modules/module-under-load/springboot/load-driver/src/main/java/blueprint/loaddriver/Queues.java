package blueprint.loaddriver;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;

/**
 * Reads the queues of the application, once per sample. Each one can run out, and each one
 * names a different setting when it does.
 *
 * <p>
 * The numbers come from where the application already publishes them: the meters of
 * VanillaBP, of the connection pool and of the BPMS adapter in Micrometer, and the garbage
 * collector of the JVM. Nothing here is a meter of its own. So what this driver reads is what
 * your monitoring would read in production.
 * </p>
 *
 * <p>
 * A meter which is not there gives {@code NaN}, for example the execution slots on an engine
 * which does not have any. The report then says that the number was not published, instead of
 * reading it as zero.
 * </p>
 */
final class Queues {

  /** Every delivery of a task to the application. Their rate is the work done. */
  static final String DELIVERIES = "vanillabp.task.deliveries";

  static final String OUTBOX_PENDING = "vanillabp.outbox.pending";

  static final String OUTBOX_OLDEST_AGE = "vanillabp.outbox.oldest.pending.age";

  static final String SLOTS_IN_USE = "vanillabp.camunda8.execution.slots.in.use";

  static final String SLOTS_CONFIGURED = "vanillabp.camunda8.execution.slots.configured";

  private final MeterRegistry meters;

  private final String connectionsWaitingMeter;

  /**
   * @param meters                  Where the application publishes its meters.
   * @param connectionsWaitingMeter The gauge of the connection pool which counts the threads
   *                                waiting for a connection. Each pool names it differently.
   */
  Queues(
      final MeterRegistry meters,
      final String connectionsWaitingMeter) {

    this.meters = meters;
    this.connectionsWaitingMeter = connectionsWaitingMeter;

  }

  /**
   * One look at every queue.
   *
   * @param atMillis       When the sample was taken.
   * @param startsWaiting  How many customers wait to hand in their request.
   * @param started        How many workflows were started so far.
   * @return The sample.
   */
  Sample sample(
      final long atMillis,
      final int startsWaiting,
      final long started) {

    final var deliveries = counted(DELIVERIES);
    final var connectionsWaiting = gauge(connectionsWaitingMeter, false);
    final var outboxPending = gauge(OUTBOX_PENDING, false);
    final var outboxOldestAge = gauge(OUTBOX_OLDEST_AGE, true);
    final var slotsInUse = gauge(SLOTS_IN_USE, false);
    final var slotsConfigured = gauge(SLOTS_CONFIGURED, false);

    return new Sample(atMillis, started, startsWaiting, deliveries, connectionsWaiting, outboxPending, outboxOldestAge, slotsInUse, slotsConfigured, collectionMillis(), liveSetBytes());

  }

  /**
   * One look at every queue.
   *
   * @param atMillis           When it was taken.
   * @param started            Workflows started so far.
   * @param startsWaiting      Customers waiting to hand in their request.
   * @param deliveries         Deliveries so far.
   * @param connectionsWaiting Threads waiting for a database connection.
   * @param outboxPending      Outbox entries waiting for their dispatch.
   * @param outboxOldestAge    How long the oldest of them has waited, in seconds.
   * @param slotsInUse         Execution slots of the adapter in use.
   * @param slotsConfigured    Execution slots of the adapter there are.
   * @param collectionMillis   Time the JVM spent collecting garbage so far.
   * @param liveSetBytes       What was left on the heap after the last collection.
   */
  record Sample(
                long atMillis,
                long started,
                int startsWaiting,
                double deliveries,
                double connectionsWaiting,
                double outboxPending,
                double outboxOldestAge,
                double slotsInUse,
                double slotsConfigured,
                long collectionMillis,
                long liveSetBytes) {
  }

  private double counted(
      final String name) {

    final var counters = Search
        .in(meters)
        .name(name)
        .counters();
    if (counters.isEmpty()) {
      return 0;
    }
    return counters
        .stream()
        .mapToDouble(counter -> counter.count())
        .sum();

  }

  private double gauge(
      final String name,
      final boolean highest) {

    final var gauges = Search
        .in(meters)
        .name(name)
        .gauges();
    if (gauges.isEmpty()) {
      return Double.NaN;
    }
    final var values = gauges
        .stream()
        .mapToDouble(gauge -> gauge.value())
        .filter(value -> !Double.isNaN(value));
    return highest
        ? values
            .max()
            .orElse(Double.NaN)
        : values.sum();

  }

  private static long collectionMillis() {

    return ManagementFactory
        .getGarbageCollectorMXBeans()
        .stream()
        .mapToLong(collector -> Math.max(0, collector.getCollectionTime()))
        .sum();

  }

  /**
   * What the heap held right after the last collection. This is the live set: the objects
   * something still points at. The memory used right now says little, because it includes
   * garbage nobody has collected yet.
   */
  static long liveSetBytes() {

    return ManagementFactory
        .getMemoryPoolMXBeans()
        .stream()
        .filter(pool -> pool.getType() == MemoryType.HEAP)
        .map(pool -> pool.getCollectionUsage())
        .filter(usage -> usage != null)
        .mapToLong(usage -> usage.getUsed())
        .sum();

  }

}
