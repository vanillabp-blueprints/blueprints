package blueprint.testdriver.loanapproval.steps;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.config.ProcessDriverProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Waiting, which every step needs. The driver waits for the aggregate and never asks the BPMS.
 * The aggregate means the same on every BPMS, and the business code writes into it at every wait
 * state: the id of an open user task, a contract sent, a signature.
 *
 * <p>
 * Every read and every call of the business code runs in a transaction of its own, which starts
 * with an empty cache of the entity manager. The driver also runs inside a request of its REST
 * API, and there Spring Boot keeps one entity manager open for the whole request
 * ({@code spring.jpa.open-in-view}). Each transaction of the request uses it. Without clearing
 * it, it would answer every read from what it loaded before, and the driver would never see
 * what the workflow wrote meanwhile.
 * </p>
 */
@Component
public class StepSupport {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private final TransactionTemplate ownTransaction;

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private ProcessDriverProperties properties;

  StepSupport(
      final PlatformTransactionManager transactionManager) {

    ownTransaction = new TransactionTemplate(transactionManager);
    ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

  }

  /**
   * Runs a call of the business code in a transaction of its own, so it reads what the workflow
   * wrote meanwhile.
   *
   * @param call The call, for example {@code Service.assessRisk}.
   */
  public void inOwnTransaction(
      final Runnable call) {

    ownTransaction.executeWithoutResult(status -> {
      entityManager.clear();
      call.run();
    });

  }

  /**
   * Waits until the aggregate of the loan request shows what the step expects.
   *
   * @param scenario  The loan request on its way.
   * @param step      The step which waits.
   * @param reached   What the aggregate shows once the step is done.
   * @param diagnosis Says why the state was not reached, from the aggregate as it was last seen.
   * @return The aggregate as it showed the expected state.
   * @throws ProcessStateNotReachedException If the time of the driver runs out. The message
   *                                         carries the diagnosis.
   */
  public Aggregate awaitLoanApproval(
      final Scenario scenario,
      final Step step,
      final Predicate<Aggregate> reached,
      final Function<Aggregate, String> diagnosis) {

    final var lastSeen = new AtomicReference<Aggregate>();

    try {

      await()
          .atMost(properties.getTimeout())
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            // A fresh read per poll, so the writes of the BPMS' own transactions become visible.
            final var found = read(scenario);
            lastSeen.set(found);
            return found != null && reached.test(found);
          });

    } catch (ConditionTimeoutException e) {

      final var aggregate = lastSeen.get();
      throw new ProcessStateNotReachedException(scenario, step, (aggregate == null
          ? "there is no aggregate at all, so the workflow never started"
          : diagnosis.apply(aggregate))
          + " (waited "
          + properties.getTimeout()
          + ", last seen: "
          + aggregate
          + ")");

    }

    return lastSeen.get();

  }

  /**
   * The aggregate of the loan request as it is now.
   *
   * @param scenario The loan request on its way.
   * @return The aggregate.
   */
  public Aggregate loanApprovalOf(
      final Scenario scenario) {

    final var loanApproval = read(scenario);
    if (loanApproval == null) {
      throw new IllegalStateException("No loan approval '"
          + scenario.getLoanRequestId()
          + "'");
    }
    return loanApproval;

  }

  private Aggregate read(
      final Scenario scenario) {

    return ownTransaction.execute(status -> {
      entityManager.clear();
      return loanApprovals
          .findById(scenario.getLoanRequestId())
          .orElse(null);
    });

  }

}
