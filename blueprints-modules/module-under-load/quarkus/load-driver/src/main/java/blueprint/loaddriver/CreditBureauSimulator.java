package blueprint.loaddriver;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import blueprint.workflowmodule.loanapproval.CreditBureau;
import blueprint.workflowmodule.loanapproval.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * Plays the credit bureau: it answers after a latency, and sometimes not at all, as the load
 * profile says.
 *
 * <p>
 * This is the neighbour system of the workflow module. A real application puts a client of
 * the real bureau here, and nothing in the module changes.
 * </p>
 */
@Slf4j
public class CreditBureauSimulator implements CreditBureau {

  /** The element of the process which waits for the answer. The profile is keyed by it. */
  static final String ANSWER_ELEMENT = "CatchEvent_CreditScoreReceived";

  private final Supplier<Service> loanApproval;

  private final LoadProfile.Neighbour bureau;

  private final ScheduledExecutorService answers = Executors.newScheduledThreadPool(
      4,
      Threads.named("credit-bureau"));

  /**
   * @param loanApproval The service the answers go to. It is looked up on each answer,
   *                     because the service itself needs this bureau.
   * @param profile      The load profile which says how the bureau behaves.
   */
  public CreditBureauSimulator(
      final Supplier<Service> loanApproval,
      final LoadProfile profile) {

    this.loanApproval = loanApproval;
    this.bureau = profile.neighbourOf(ANSWER_ELEMENT);

  }

  @Override
  public void requestScore(
      final String loanRequestId,
      final int amount) {

    if (ThreadLocalRandom
        .current()
        .nextDouble() < bureau.noAnswerShare()) {
      return;
    }

    final var latency = Spread.around(bureau.latency(), bureau.latencySpread());
    answers.schedule(
        () -> answer(loanRequestId, amount),
        latency.toMillis(),
        TimeUnit.MILLISECONDS);

  }

  /** Stops answering. Questions asked afterwards stay without an answer. */
  public void stop() {

    answers.shutdownNow();

  }

  private void answer(
      final String loanRequestId,
      final int amount) {

    try {
      loanApproval
          .get()
          .creditScoreReceived(loanRequestId, Math.min(100, amount / 500));
    } catch (final RuntimeException e) {
      log.warn("The {} could not deliver its answer: {}", bureau.name(), e.toString());
    }

  }

}
