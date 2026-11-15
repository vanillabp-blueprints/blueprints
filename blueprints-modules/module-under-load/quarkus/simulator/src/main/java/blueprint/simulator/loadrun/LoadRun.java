package blueprint.simulator.loadrun;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;

/**
 * A run of the load driver. It walks through the stages, watches the queues and writes the
 * report at the end.
 *
 * <p>
 * The stages: a warm up, so the JVM and the pools are ready; a steady stage at the rate of the
 * load profile, which is the baseline; steps which offer a quarter more each; a peak; and a
 * drain, in which nobody arrives any more, until every workflow has ended and the outbox is
 * empty. The drain is part of the result. A run whose work does not end has not shown a
 * limit, it has hidden one.
 * </p>
 *
 * <p>
 * The run plays behaviour, not a loop. Customers arrive at a rate and do not wait for each
 * other. Clerks hold a task while they think. The credit bureau answers late, and sometimes
 * not at all. Approved loans wait for the next payout run and wake up together.
 * </p>
 *
 * <p>
 * The run happens in the simulator, a process of its own. It reaches the application only from
 * outside: the customers and the clerks through its HTTP API, the queues through the meters it
 * publishes, and the full collections through JMX. So the threads of the driver do not take CPU,
 * heap or collection time from the application it measures.
 * </p>
 */
public class LoadRun {

  private static final Logger log = LoggerFactory.getLogger(LoadRun.class);

  /** How often the queues are read. */
  private static final Duration SAMPLE_EVERY = Duration.ofSeconds(1);

  /** How much more each step offers than the steady stage. */
  private static final double STEP = 0.25;

  private final LoanApprovalApi loanApproval;

  private final LoadProfile profile;

  private final Queues queues;

  private final FullCollection fullCollection;

  private final String poolSetting;

  private final Map<String, RunSize> sizes;

  private final int customerThreads;

  private final AtomicBoolean running = new AtomicBoolean();

  private final ExecutorService runner = Executors.newSingleThreadExecutor(Threads.named("run"));

  private volatile String progress = "No run has started yet.";

  private volatile Report lastReport;

  /**
   * @param loanApproval    The client of the workflow module's API, which the customers and
   *                        the clerks call.
   * @param profile                 The load profile.
   * @param meters                  The client which reads the meters of the application.
   * @param metricsUrl              Where the application shows its meters in the Prometheus
   *                                format.
   * @param connectionsWaitingMeter The gauge of the connection pool which counts the threads
   *                                waiting for a connection, in the Prometheus format.
   * @param jmxAddress              Where the JVM of the application answers on JMX, as
   *                                {@code host:port}.
   * @param poolSetting             The setting of the connection pool of the application, for
   *                                the report.
   * @param sizes                   The run sizes, by name.
   * @param customerThreads         How many loan requests may be handed in at the same time.
   */
  public LoadRun(
      final LoanApprovalApi loanApproval,
      final LoadProfile profile,
      final RestClient meters,
      final String metricsUrl,
      final String connectionsWaitingMeter,
      final String jmxAddress,
      final String poolSetting,
      final Map<String, RunSize> sizes,
      final int customerThreads) {

    sizes.forEach((
        name,
        size) -> {
      if (size.stageLength() == null || size.stageLength().isNegative() || size.stageLength().isZero() || size
          .steps() < 0 || size.drainAtMost() == null || size.drainAtMost().isNegative()) {
        throw new IllegalStateException("'load-run.sizes."
            + name
            + "' needs 'stage-length'"
            + " and 'drain-at-most' as durations above zero, such as PT30S, and 'steps' as a"
            + " number of zero or more.");
      }
    });
    if (customerThreads < 1) {
      throw new IllegalStateException("'load-run.customer-threads' is "
          + customerThreads
          + ". It"
          + " says how many loan requests may be handed in at the same time, so it needs at"
          + " least 1. This blueprint uses 16.");
    }
    if (sizes.isEmpty()) {
      throw new IllegalStateException("'load-run.sizes' names no size. Add at least one, for"
          + " example 'small' with 'stage-length: PT30S', 'steps: 3' and 'drain-at-most: PT2M'.");
    }
    this.loanApproval = loanApproval;
    this.profile = profile;
    this.queues = new Queues(meters, metricsUrl, connectionsWaitingMeter);
    this.fullCollection = new FullCollection(jmxAddress);
    this.poolSetting = poolSetting;
    this.sizes = Map.copyOf(sizes);
    this.customerThreads = customerThreads;

  }

  /**
   * Starts a run in the background.
   *
   * @param size      The name of the run size.
   * @param reportUrl Where the report of the run is, for the sentence.
   * @return A sentence for the browser.
   */
  public String start(
      final String size,
      final String reportUrl) {

    if (!sizes.containsKey(size)) {
      return "There is no run size '"
          + size
          + "'. Known: "
          + sizes.keySet();
    }
    if (running.get()) {
      return "A run is going on. "
          + progress;
    }
    runner.execute(() -> {
      try {
        run(size);
      } catch (final RuntimeException e) {
        // Nobody waits for this thread, so the report is where the reason shows.
        progress = "The run '"
            + size
            + "' stopped: "
            + e.getMessage();
        log.error("The run '{}' stopped", size, e);
      }
    });
    return "The run '"
        + size
        + "' started. Its report -> "
        + reportUrl;

  }

  /**
   * @return The report of the last run, or how far the current one has come.
   */
  public String report() {

    if (running.get() || lastReport == null) {
      return progress;
    }
    return lastReport.toString();

  }

  /**
   * Runs one load run and waits for its end.
   *
   * @param size The name of the run size.
   * @return The report.
   */
  public Report run(
      final String size) {

    final var runSize = sizes.get(size);
    if (runSize == null) {
      throw new IllegalArgumentException("There is no run size '"
          + size
          + "'. Known: "
          + sizes.keySet());
    }
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("A run is going on already.");
    }
    try {
      // Both are needed later. Asking now stops the run at once if the application is not
      // there, instead of after the warm up.
      queues.liveSetBytes();
      fullCollection.ask();
    } catch (final RuntimeException e) {
      running.set(false);
      throw e;
    }

    final var startedAt = System.currentTimeMillis();
    final var customers = new Customers(loanApproval, profile, customerThreads);
    final var clerks = new Clerks(loanApproval, profile);
    final var results = new ArrayList<StageResult>();
    var liveSetAfterWarmUp = 0L;
    var liveSetAfterDrain = 0L;
    var unfinished = 0L;

    try {

      log.info("Load run '{}' starts", size);
      var before = sample(customers);
      for (final var stage : stages(runSize)) {
        progress = "Stage '"
            + stage.name()
            + "' of the run '"
            + size
            + "' is going on.";
        customers.arrive(stage.perSecond());
        final var samples = new ArrayList<Queues.Sample>();
        final var endsAt = System.currentTimeMillis() + stage.length().toMillis();
        while (System.currentTimeMillis() < endsAt) {
          Thread.sleep(SAMPLE_EVERY);
          samples.add(sample(customers));
        }
        results.add(StageResult.of(stage.name(), stage.perSecond(), before, samples));
        log.info("Stage '{}' is over", stage.name());
        if (!samples.isEmpty()) {
          before = samples.getLast();
        }
        if (results.size() == 1) {
          liveSetAfterWarmUp = liveSetAfterFullCollection();
        }
      }

      progress = "The run '"
          + size
          + "' drains: nobody arrives, the work left is done.";
      customers.arrive(0);
      final var samples = new ArrayList<Queues.Sample>();
      final var givesUpAt = System.currentTimeMillis() + runSize.drainAtMost().toMillis();
      do {
        Thread.sleep(SAMPLE_EVERY);
        samples.add(sample(customers));
        unfinished = loanApproval.unfinished();
      } while ((unfinished > 0 || samples.getLast().outboxPending() > 0) && System.currentTimeMillis() < givesUpAt);
      results.add(StageResult.of("drain", 0, before, samples));
      liveSetAfterDrain = liveSetAfterFullCollection();

    } catch (final InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
      throw new IllegalStateException("The run '"
          + size
          + "' was interrupted", e);
    } finally {
      customers.stop();
      clerks.stop();
      running.set(false);
    }

    final var took = Duration.ofMillis(System.currentTimeMillis() - startedAt);
    final var failures = customers.failed() + clerks.failed();
    final var report = new Report(size, profile, List
        .copyOf(results), took, unfinished, failures, liveSetAfterWarmUp, liveSetAfterDrain, poolSetting);
    lastReport = report;
    log.info("{}{}", System.lineSeparator(), report);
    return report;

  }

  /** Stops a run which is going on, when the simulator shuts down. */
  public void stop() {

    runner.shutdownNow();

  }

  private record Stage(
                       String name,
                       double perSecond,
                       Duration length) {
  }

  private List<Stage> stages(
      final RunSize size) {

    final var steady = profile
        .arrivals()
        .perSecond();
    final var stages = new ArrayList<Stage>();
    stages.add(new Stage("warm-up", steady, size.stageLength()));
    stages.add(new Stage("steady", steady, size.stageLength()));
    for (var step = 1; step <= size.steps(); ++step) {
      stages.add(new Stage("step-"
          + step, steady * (1 + STEP * step), size.stageLength()));
    }
    // The peak jumps two steps at once, the way a real peak does not announce itself.
    stages.add(new Stage("peak", steady * (1 + STEP * (size.steps() + 2)), size.stageLength()));
    return stages;

  }

  private Queues.Sample sample(
      final Customers customers) {

    return queues.sample(System.currentTimeMillis(), customers.waiting(), customers.started());

  }

  /**
   * The live set of the application after a full collection. The run asks for one twice, after
   * the warm up and after the drain, so the two numbers can be compared: without it, the heap
   * would also hold garbage which nobody has collected yet. An application never does this
   * itself. The driver asks from outside, as an operator could.
   */
  private long liveSetAfterFullCollection() throws InterruptedException {

    fullCollection.ask();
    // The meter of the live set is set when the JVM reports the end of the collection, a
    // moment after the call returned.
    Thread.sleep(SAMPLE_EVERY);
    return queues.liveSetBytes();

  }

}
