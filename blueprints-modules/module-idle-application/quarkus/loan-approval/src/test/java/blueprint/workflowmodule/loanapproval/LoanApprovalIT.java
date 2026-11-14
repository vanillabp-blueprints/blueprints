package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

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
 * the clock run and look again. Before the clock starts, the test waits until the engine
 * has finished the work of the service task, because that work is not idle traffic.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  /**
   * How long the test watches the quiet application. It has to be longer than the interval
   * an application without these properties polls at, ten seconds, so that a poller which
   * is still running cannot slip through the window unnoticed. It also has to stay well
   * inside the thirty seconds the workflow waits.
   */
  private static final Duration IDLE_WINDOW = Duration.ofSeconds(15);

  /**
   * How long the number of statements has to stay the same before the test calls the engine
   * quiet. The credit rating is written by the service task itself, and after that the engine
   * still commits the job and creates the timer. Those statements are not idle traffic, so
   * the window must not start before they are done. The span is much shorter than the ten
   * seconds a poller waits between two runs, so a poller cannot pass for quiet: it is caught
   * in the window anyway.
   */
  private static final Duration QUIET_SPAN = Duration.ofSeconds(2);

  /**
   * How long the test waits for the engine to become quiet. After that it measures anyway,
   * and a database which is still busy shows up in the assertion. Together with the idle
   * window it stays inside the thirty seconds the workflow waits.
   */
  private static final Duration QUIET_LIMIT = Duration.ofSeconds(10);

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  DatabaseTraffic traffic;

  @Test
  public void nothingTouchesTheDatabaseWhileTheWorkflowWaits() throws Exception {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    // The count comes out of the file H2 writes its statements into, so asking for it is not
    // itself a statement and the measurement does not pay for being taken.
    final var beforeWaiting = statementsOnceTheEngineIsQuiet();
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
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

    assertThat(loanRequest.getCreditRating()).isEqualTo(50);

  }

  /**
   * Waits until the database has seen no new statement for {@link #QUIET_SPAN}, but not
   * longer than {@link #QUIET_LIMIT}. The trace file only grows, so two equal readings mean
   * that nothing happened between them.
   *
   * @return The number of statements at the moment the engine was quiet, or at the limit.
   */
  private long statementsOnceTheEngineIsQuiet() throws InterruptedException {

    final var giveUpAt = System.nanoTime() + QUIET_LIMIT.toNanos();
    var statements = traffic.statementsSoFar();
    while (System.nanoTime() < giveUpAt) {
      Thread.sleep(QUIET_SPAN.toMillis());
      final var statementsNow = traffic.statementsSoFar();
      if (statementsNow == statements) {
        return statements;
      }
      statements = statementsNow;
    }
    return statements;

  }

}
