package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work.
 *
 * <p>
 * This is the level a blueprint proves its aspect on, and the level generated code has to
 * be verified on. Everything not specific to this blueprint - booting the module, waiting
 * for progress - comes from {@link WorkflowModuleTest}, so that what remains here is the
 * aspect and nothing else.
 * </p>
 *
 * <p>
 * The aspect here is a span of time in which nothing may happen, which is why this test
 * sleeps instead of waiting for a condition. There is nothing to wait for: the assertion is
 * that the database saw no statement at all, and the only way to find that out is to let
 * the clock run and look again.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  /**
   * How long the test watches the quiet application. It has to be longer than the interval
   * an application without these properties polls at, ten seconds, so that a poller which
   * is still running cannot slip through the window unnoticed. It also has to stay well
   * inside the thirty seconds the workflow waits.
   */
  private static final Duration IDLE_WINDOW = Duration.ofSeconds(15);

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private DatabaseTraffic traffic;

  @Test
  public void nothingTouchesTheDatabaseWhileTheWorkflowWaits() throws Exception {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    // The count comes out of the file H2 writes its statements into, so asking for it is not
    // itself a statement and the measurement does not pay for being taken.
    final var beforeWaiting = traffic.statementsSoFar();
    Thread.sleep(IDLE_WINDOW.toMillis());
    final var afterWaiting = traffic.statementsSoFar();

    assertThat(afterWaiting)
        .describedAs(
            "Statements the database was asked while the workflow waited in its timer."
                + " None are expected, so a number which grew means a poller is still"
                + " running: check that both keys arrived and that the profile file carrying"
                + " the engine key is loaded here.")
        .isEqualTo(beforeWaiting);

    // ... and the workflow still goes on when the timer is due, which is the half nobody
    // may trade away for the quiet above.
    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

}
