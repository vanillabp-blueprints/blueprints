package blueprint.simulator.loadrun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Reads the queues of the application, once per sample. Each one can run out, and each one
 * names a different setting when it does.
 *
 * <p>
 * The numbers come from where the application already publishes them: the meters of VanillaBP,
 * of the connection pool, of the BPMS adapter and of the JVM, which Micrometer shows in the
 * Prometheus format. The simulator reads them over HTTP, from outside, like a monitoring system
 * would. Nothing here is a meter of its own, and the application has no meter made for this
 * driver. So what this driver reads is what your monitoring would read in production.
 * </p>
 *
 * <p>
 * A meter which is not there gives {@code NaN}, for example the execution slots on an engine
 * which does not have any. The report then says that the number was not published, instead of
 * reading it as zero.
 * </p>
 */
final class Queues {

  /*
   * The names below are the names of the meters in the Prometheus format: dots become
   * underscores, a counter ends with '_total', and a meter with a unit ends with that unit.
   */

  /** Every delivery of a task to the application. Their rate is the work done. */
  static final String DELIVERIES = "vanillabp_task_deliveries_total";

  static final String OUTBOX_PENDING = "vanillabp_outbox_pending";

  static final String OUTBOX_OLDEST_AGE = "vanillabp_outbox_oldest_pending_age_seconds";

  static final String SLOTS_IN_USE = "vanillabp_camunda8_execution_slots_in_use";

  static final String SLOTS_CONFIGURED = "vanillabp_camunda8_execution_slots_configured";

  /** The time the JVM stopped the application to collect garbage, in seconds. */
  static final String COLLECTION_PAUSES = "jvm_gc_pause_seconds_sum";

  /** The time the collector worked next to the application, in seconds. */
  static final String COLLECTION_CONCURRENT = "jvm_gc_concurrent_phase_time_seconds_sum";

  /** What the heap held after the last full collection, in bytes. */
  static final String LIVE_SET = "jvm_gc_live_data_size_bytes";

  private final RestClient meters;

  private final String metricsUrl;

  private final String connectionsWaitingMeter;

  /**
   * @param meters                  The client which reads the meters.
   * @param metricsUrl              Where the application shows its meters in the Prometheus
   *                                format.
   * @param connectionsWaitingMeter The gauge of the connection pool which counts the threads
   *                                waiting for a connection. Each pool names it differently.
   */
  Queues(
      final RestClient meters,
      final String metricsUrl,
      final String connectionsWaitingMeter) {

    this.meters = meters;
    this.metricsUrl = metricsUrl;
    this.connectionsWaitingMeter = connectionsWaitingMeter;

  }

  /**
   * One look at every queue.
   *
   * @param atMillis      When the sample was taken.
   * @param startsWaiting How many customers wait to hand in their request.
   * @param started       How many workflows were started so far.
   * @return The sample.
   */
  Sample sample(
      final long atMillis,
      final int startsWaiting,
      final long started) {

    final var values = read();

    final var deliveries = sum(values, DELIVERIES, 0);
    final var connectionsWaiting = sum(values, connectionsWaitingMeter, Double.NaN);
    final var outboxPending = sum(values, OUTBOX_PENDING, Double.NaN);
    final var outboxOldestAge = highest(values, OUTBOX_OLDEST_AGE);
    final var slotsInUse = sum(values, SLOTS_IN_USE, Double.NaN);
    final var slotsConfigured = sum(values, SLOTS_CONFIGURED, Double.NaN);
    final var collectionMillis = Math.round(
        (sum(values, COLLECTION_PAUSES, 0) + sum(values, COLLECTION_CONCURRENT, 0)) * 1000);
    final var liveSetBytes = Math.round(sum(values, LIVE_SET, 0));

    return new Sample(atMillis, started, startsWaiting, deliveries, connectionsWaiting, outboxPending, outboxOldestAge, slotsInUse, slotsConfigured, collectionMillis, liveSetBytes);

  }

  /**
   * What the heap of the application held after its last full collection. This is the live
   * set: the objects something still points at. The memory used right now says little, because
   * it includes garbage nobody has collected yet.
   *
   * @return The live set in bytes.
   */
  long liveSetBytes() {

    return Math.round(sum(read(), LIVE_SET, 0));

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
   * @param collectionMillis   Time the JVM of the application spent collecting garbage so far.
   * @param liveSetBytes       What was left on its heap after the last full collection.
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

  /**
   * Reads every meter of the application once. A meter with tags comes once per combination of
   * tags, so each name maps to all of its values.
   */
  private Map<String, List<Double>> read() {

    final var text = meters
        .get()
        .uri(metricsUrl)
        .accept(MediaType.TEXT_PLAIN)
        .retrieve()
        .body(String.class);

    final var values = new HashMap<String, List<Double>>();
    if (text == null) {
      return values;
    }
    text
        .lines()
        .filter(line -> !line.isBlank() && !line.startsWith("#"))
        .forEach(line -> {
          // A line is 'name value' or 'name{tags} value'. A tag value may hold a space, so the
          // value is what follows the closing brace.
          final var tagsEnd = line.lastIndexOf('}');
          final var nameEnd = line.indexOf(tagsEnd < 0 ? ' ' : '{');
          if (nameEnd < 0) {
            return;
          }
          final var rest = line
              .substring(tagsEnd < 0 ? nameEnd : tagsEnd + 1)
              .trim()
              .split(" ");
          values
              .computeIfAbsent(line.substring(0, nameEnd), name -> new ArrayList<>())
              .add(number(rest[0]));
        });
    return values;

  }

  private static double number(
      final String value) {

    return switch (value) {
      case "+Inf" -> Double.POSITIVE_INFINITY;
      case "-Inf" -> Double.NEGATIVE_INFINITY;
      default -> Double.parseDouble(value);
    };

  }

  private static double sum(
      final Map<String, List<Double>> values,
      final String name,
      final double whenMissing) {

    final var found = values.get(name);
    if (found == null) {
      return whenMissing;
    }
    return found
        .stream()
        .mapToDouble(Double::doubleValue)
        .filter(value -> !Double.isNaN(value))
        .sum();

  }

  private static double highest(
      final Map<String, List<Double>> values,
      final String name) {

    final var found = values.get(name);
    if (found == null) {
      return Double.NaN;
    }
    return found
        .stream()
        .mapToDouble(Double::doubleValue)
        .filter(value -> !Double.isNaN(value))
        .max()
        .orElse(Double.NaN);

  }

}
