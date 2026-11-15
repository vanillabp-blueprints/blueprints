package blueprint.simulator.loadrun;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.api.client.model.LoanRequest;

/**
 * The customers: they request loans at a rate, whether the application keeps up or not.
 *
 * <p>
 * A customer does not wait for the one before. Each arrival hands its request to a pool of
 * threads, which send it to the API of the application over HTTP, as the front ends of many
 * customers would. If the application gets slow, the requests queue up in front of that pool,
 * and {@link #waiting()} says how many.
 * </p>
 */
final class Customers {

  private static final Logger log = LoggerFactory.getLogger(Customers.class);

  private final LoanApprovalApi loanApproval;

  private final LoadProfile profile;

  private final String documents;

  private final ScheduledExecutorService arrivals = Executors.newSingleThreadScheduledExecutor(
      Threads.named("customer-arrivals"));

  private final ThreadPoolExecutor requests;

  private final AtomicLong started = new AtomicLong();

  private final AtomicLong failed = new AtomicLong();

  private volatile double perSecond;

  Customers(
      final LoanApprovalApi loanApproval,
      final LoadProfile profile,
      final int threads) {

    this.loanApproval = loanApproval;
    this.profile = profile;
    this.documents = "x".repeat(profile
        .aggregate()
        .sizeBytes());
    this.requests = new ThreadPoolExecutor(
        threads, threads, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), Threads.named("customer"));
    arrivals.schedule(this::nextArrival, 0, TimeUnit.MILLISECONDS);

  }

  /**
   * Changes how many customers arrive per second. Zero stops the arrivals.
   *
   * @param perSecond The new rate.
   */
  void arrive(
      final double perSecond) {

    this.perSecond = perSecond;

  }

  /**
   * @return How many loan requests were handed in so far.
   */
  long started() {

    return started.get();

  }

  /**
   * @return How many loan requests failed, which should stay zero.
   */
  long failed() {

    return failed.get();

  }

  /**
   * @return How many customers wait in front of the request threads right now.
   */
  int waiting() {

    return requests
        .getQueue()
        .size();

  }

  void stop() {

    arrivals.shutdownNow();
    requests.shutdownNow();

  }

  private void nextArrival() {

    final var rate = perSecond;
    if (rate <= 0) {
      // Nobody arrives. Look again in a moment, the rate may have changed.
      arrivals.schedule(this::nextArrival, 100, TimeUnit.MILLISECONDS);
      return;
    }

    requests.execute(this::requestLoan);

    final var averageGapMillis = 1000.0 / rate;
    final var gapMillis = profile
        .arrivals()
        .pattern()
        .equals("random")
            // Gaps of real arrivals: most are short, a few are long, the average is the same.
            ? -Math.log(1.0 - ThreadLocalRandom
                .current()
                .nextDouble()) * averageGapMillis
            : averageGapMillis;
    arrivals.schedule(this::nextArrival, Math.round(gapMillis * 1000), TimeUnit.MICROSECONDS);

  }

  private void requestLoan() {

    try {

      loanApproval.request(new LoanRequest(
          ThreadLocalRandom
              .current()
              .nextInt(1000, 50000),
          documents));
      started.incrementAndGet();

    } catch (final RuntimeException e) {

      failed.incrementAndGet();
      log.warn("A loan request failed: {}", e.toString());

    }

  }

}
