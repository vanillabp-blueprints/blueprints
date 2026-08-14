package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts real workflows in a real BPMS,
 * waits for them to have reached the signal event, broadcasts the signal and watches all of
 * them continue.
 *
 * <p>
 * What is asserted is the workflow aggregate, never the engine - a waiting workflow shows
 * itself by what has NOT happened yet.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  private static final BigDecimal RATE = new BigDecimal("3.5");

  /** Long enough to catch a workflow continuing when it should not, short enough to wait for. */
  private static final Duration GRACE = Duration.ofSeconds(3);

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  private String startAndAwaitTheWaitingWorkflow() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    return loanRequestId;

  }

  /**
   * Broadcasts the signal until the workflows reacted.
   *
   * <p>
   * <b>A signal is not buffered</b>, and that makes it a different thing to test than a
   * message. It reaches whoever waits at that very moment, so a test may not send it once
   * and then wait: the credit rating being written proves the service task ran, not that
   * the subscription of the catch event behind it already exists - on a remote engine those
   * are two transactions. Sending repeatedly is the honest way to wait for a broadcast, and
   * a repeat is harmless because a signal nobody waits for reaches nobody.
   * </p>
   *
   * @param reacted What the broadcast is supposed to achieve.
   */
  private void broadcastUntil(
      final BooleanSupplier reacted) {

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> {
          service.publishInterestRate(RATE);
          return reacted.getAsBoolean();
        });

  }

  private boolean wasOffered(
      final String loanRequestId) {

    return loanApprovals
        .findById(loanRequestId)
        .filter(loanApproval -> loanApproval.getInterestRate() != null)
        .isPresent();

  }

  @Test
  @DisplayName("The workflow waits for the signal")
  public void theWorkflowWaitsForTheSignal() {

    final var loanRequestId = startAndAwaitTheWaitingWorkflow();

    // the service task behind the signal event has not run, so the workflow is standing at
    // the catch event rather than having passed it
    assertThat(loanApprovals.findById(loanRequestId).orElseThrow().getInterestRate()).isNull();

  }

  @Test
  @DisplayName("One broadcast continues every waiting workflow")
  public void oneBroadcastContinuesEveryWaitingWorkflow() {

    final var firstLoanRequestId = startAndAwaitTheWaitingWorkflow();
    final var secondLoanRequestId = startAndAwaitTheWaitingWorkflow();

    // nobody addressed either of them: the signal is broadcast, and both react
    broadcastUntil(() -> wasOffered(firstLoanRequestId) && wasOffered(secondLoanRequestId));

    final var first = awaitAggregate(
        loanApprovals,
        firstLoanRequestId,
        loanApproval -> loanApproval.getInterestRate() != null);
    final var second = awaitAggregate(
        loanApprovals,
        secondLoanRequestId,
        loanApproval -> loanApproval.getInterestRate() != null);

    // what the signal could not carry, both workflows read from the application's data
    assertThat(first.getInterestRate()).isEqualByComparingTo(RATE);
    assertThat(second.getInterestRate()).isEqualByComparingTo(RATE);

  }

  @Test
  @DisplayName("A signal is not buffered: a workflow arriving later waits for the next one")
  public void aSignalIsNotBuffered() {

    // nobody is waiting for this one, so it reaches nobody and is gone
    service.publishInterestRate(RATE);

    final var loanRequestId = startAndAwaitTheWaitingWorkflow();

    // the workflow reached the catch event after the broadcast, and stays there
    await()
        .during(GRACE)
        .atMost(GRACE.plus(TIMEOUT))
        .until(() -> !wasOffered(loanRequestId));

    // the next publication is the one it gets
    broadcastUntil(() -> wasOffered(loanRequestId));

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getInterestRate() != null);

    assertThat(loanApproval.getInterestRate()).isEqualByComparingTo(RATE);

  }

}
