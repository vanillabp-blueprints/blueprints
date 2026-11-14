package blueprint.loaddriver;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * What one stage of a run did, condensed from the samples taken during it.
 *
 * @param name               The name of the stage.
 * @param offeredPerSecond   How many workflows per second the customers tried to start.
 * @param startedPerSecond   How many workflows per second were started.
 * @param deliveriesPerSecond How many task deliveries per second the application got, which
 *                           is the work done.
 * @param startsWaiting      Customers waiting to hand in their request, on average.
 * @param connectionsWaiting Threads waiting for a database connection, on average.
 * @param outboxPending      Outbox entries waiting for their dispatch, on average.
 * @param outboxOldestAge    How long the oldest entry waited, the highest value seen, in
 *                           seconds.
 * @param slotsInUse         Execution slots of the adapter in use, on average.
 * @param slotsConfigured    Execution slots there are.
 * @param collectionShare    The share of the time the JVM spent collecting garbage.
 * @param liveSetBytes       The live set at the end of the stage.
 */
record StageResult(
                   String name,
                   double offeredPerSecond,
                   double startedPerSecond,
                   double deliveriesPerSecond,
                   double startsWaiting,
                   double connectionsWaiting,
                   double outboxPending,
                   double outboxOldestAge,
                   double slotsInUse,
                   double slotsConfigured,
                   double collectionShare,
                   long liveSetBytes) {

  /**
   * Condenses the samples of one stage.
   *
   * @param name             The name of the stage.
   * @param offeredPerSecond The rate the customers tried.
   * @param before           The last sample before the stage, so the first second counts.
   * @param samples          The samples taken during the stage.
   * @return The result.
   */
  static StageResult of(
      final String name,
      final double offeredPerSecond,
      final Queues.Sample before,
      final List<Queues.Sample> samples) {

    final var last = samples.isEmpty() ? before : samples.getLast();
    final var seconds = Math.max(0.001, (last.atMillis() - before.atMillis()) / 1000.0);
    final var started = last.started() - before.started();
    final var deliveries = last.deliveries() - before.deliveries();
    final var collectionMillis = last.collectionMillis() - before.collectionMillis();
    final var oldestAge = samples
        .stream()
        .mapToDouble(Queues.Sample::outboxOldestAge)
        .filter(value -> !Double.isNaN(value))
        .max()
        .orElse(Double.NaN);

    final var startsWaiting = average(samples, Queues.Sample::startsWaiting);
    final var connectionsWaiting = average(samples, Queues.Sample::connectionsWaiting);
    final var outboxPending = average(samples, Queues.Sample::outboxPending);
    final var slotsInUse = average(samples, Queues.Sample::slotsInUse);
    final var collectionShare = collectionMillis / (seconds * 1000);

    return new StageResult(name, offeredPerSecond, started / seconds, deliveries / seconds, startsWaiting, connectionsWaiting, outboxPending, oldestAge, slotsInUse, last
        .slotsConfigured(), collectionShare, last.liveSetBytes());

  }

  private static double average(
      final List<Queues.Sample> samples,
      final ToDoubleFunction<Queues.Sample> value) {

    return samples
        .stream()
        .mapToDouble(value)
        .filter(number -> !Double.isNaN(number))
        .average()
        .orElse(Double.NaN);

  }

}
