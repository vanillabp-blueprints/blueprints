package blueprint.simulator.loadrun;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.api.client.model.OpenAssessment;

/**
 * The clerks: each one takes an open assessment from the task list, holds it while thinking,
 * and decides.
 *
 * <p>
 * The task list is the API of the application, called over HTTP. A clerk looks at it again
 * when it was empty, the way a person presses refresh. That costs the application a request
 * and the database a query, as a real task list would, so it is part of the load and not
 * hidden from it. Two clerks never take the same task, because each one claims
 * it first. A claim is never given back: a task list another clerk read a moment ago may
 * still show a task which was decided since.
 * </p>
 *
 * <p>
 * After a call which failed, a clerk waits as long as after an empty task list. So a clerk
 * never calls the application more often than twice a second while there is nothing to
 * decide, whether the application answers or not.
 * </p>
 */
final class Clerks {

  private static final Logger log = LoggerFactory.getLogger(Clerks.class);

  /** The user task the clerks work on. */
  static final String USER_TASK = "UserTask_AssessLoan";

  /** The gateway behind it, and the flow its share is read for. */
  static final String GATEWAY = "Gateway_Approved";

  static final String APPROVED_FLOW = "Flow_ToPayoutRun";

  /**
   * How long a clerk waits before looking at an empty task list again, and after a call which
   * failed.
   */
  private static final Duration REFRESH = Duration.ofMillis(500);

  /** How long stopping waits for the clerks, who are cut off in the middle of a call. */
  private static final Duration STOP_AT_MOST = Duration.ofSeconds(10);

  private final LoanApprovalApi loanApproval;

  private final LoadProfile.People people;

  private final double approvedShare;

  private final ExecutorService clerks;

  private final Set<String> claimed = ConcurrentHashMap.newKeySet();

  private final AtomicLong decided = new AtomicLong();

  private final AtomicLong failed = new AtomicLong();

  private volatile boolean working = true;

  Clerks(
      final LoanApprovalApi loanApproval,
      final LoadProfile profile) {

    this.loanApproval = loanApproval;
    this.people = profile.peopleOf(USER_TASK);
    this.approvedShare = profile.share(GATEWAY, APPROVED_FLOW);
    this.clerks = Executors.newFixedThreadPool(people.persons(), Threads.named("clerk"));
    for (var i = 0; i < people.persons(); ++i) {
      clerks.execute(this::work);
    }

  }

  /**
   * @return How many assessments the clerks decided so far.
   */
  long decided() {

    return decided.get();

  }

  /**
   * @return How many decisions failed, which should stay zero.
   */
  long failed() {

    return failed.get();

  }

  /**
   * Stops the clerks and waits until they have gone, so {@link #failed()} does not change any
   * more.
   *
   * <p>
   * The clerks look at the task list twice a second, so some of them always wait for an answer
   * when the run stops. Stopping interrupts that call, and the HTTP client reports it as an
   * error. That error is the stop, not the application, so it does not count as failed. The
   * slower the machine, the more calls are going on at that moment.
   * </p>
   */
  void stop() {

    working = false;
    clerks.shutdownNow();
    try {
      clerks.awaitTermination(STOP_AT_MOST.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
    }

  }

  private void work() {

    while (working) {
      try {

        final var assessment = takeNextAssessment();
        if (assessment == null) {
          Thread.sleep(REFRESH);
          continue;
        }
        Thread.sleep(thinkTime());
        decide(assessment);

      } catch (final InterruptedException e) {
        Thread
            .currentThread()
            .interrupt();
        return;
      } catch (final RuntimeException e) {
        if (!working) {
          // The run stopped this call, see stop().
          return;
        }
        failed.incrementAndGet();
        log.warn("A clerk could not work on an assessment: {}", e.toString());
        // An application which fails or is gone answers at once. Asking again at once would
        // turn every clerk into a tight loop and fill the log, so the clerk waits as long as
        // after an empty task list.
        if (!waitBeforeLookingAgain()) {
          return;
        }
      }
    }

  }

  /**
   * @return False if the run interrupted the wait, so the clerk stops.
   */
  private boolean waitBeforeLookingAgain() {

    try {
      Thread.sleep(REFRESH);
      return true;
    } catch (final InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
      return false;
    }

  }

  private OpenAssessment takeNextAssessment() {

    return loanApproval
        .openAssessments()
        .stream()
        .filter(assessment -> claimed.add(assessment.loanRequestId()))
        .findFirst()
        .orElse(null);

  }

  private void decide(
      final OpenAssessment assessment) {

    loanApproval
        .assess(
            assessment.loanRequestId(),
            assessment.taskId(),
            ThreadLocalRandom
                .current()
                .nextDouble() < approvedShare);
    decided.incrementAndGet();

  }

  private Duration thinkTime() {

    return Spread.around(people.thinkTime(), people.thinkTimeSpread());

  }

}
