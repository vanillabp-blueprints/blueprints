package blueprint.testdriver.loanapproval.steps;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.config.ProcessDriverProperties;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import blueprint.testdriver.loanapproval.gui.model.LoanRequest;

/**
 * Waiting, which every step needs. The driver asks the API of the user interface how far a loan
 * request came, as a person in the browser does, and never the BPMS. The module writes a trace at
 * every wait state, and the API shows it: the id of an open user task, a contract sent, a
 * signature.
 */
@Component
public class StepSupport {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  @Autowired
  private LoanApprovalApi loanApproval;

  @Autowired
  private ProcessDriverProperties properties;

  /**
   * Waits until the loan request shows what the step expects.
   *
   * @param scenario  The loan request on its way.
   * @param step      The step which waits.
   * @param reached   What the loan request shows once the step is done.
   * @param diagnosis Says why the state was not reached, from the loan request as it was last
   *                  seen.
   * @return The loan request as it showed the expected state.
   * @throws ProcessStateNotReachedException If the time of the driver runs out. The message
   *                                         carries the diagnosis.
   */
  public LoanRequest awaitLoanRequest(
      final Scenario scenario,
      final Step step,
      final Predicate<LoanRequest> reached,
      final Function<LoanRequest, String> diagnosis) {

    final var lastSeen = new AtomicReference<LoanRequest>();

    try {

      await()
          .atMost(properties.getTimeout())
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            final var found = read(scenario).orElse(null);
            lastSeen.set(found);
            return found != null && reached.test(found);
          });

    } catch (ConditionTimeoutException e) {

      final var loanRequest = lastSeen.get();
      throw new ProcessStateNotReachedException(scenario, step, (loanRequest == null
          ? "the module does not know the loan request, so the workflow never started"
          : diagnosis.apply(loanRequest))
          + " (waited "
          + properties.getTimeout()
          + ", last seen: "
          + loanRequest
          + ")");

    }

    return lastSeen.get();

  }

  /**
   * The loan request as the module shows it now.
   *
   * @param scenario The loan request on its way.
   * @return The loan request.
   */
  public LoanRequest loanRequestOf(
      final Scenario scenario) {

    return read(scenario).orElseThrow(() -> new IllegalStateException("No loan request '"
        + scenario.getLoanRequestId()
        + "'"));

  }

  private Optional<LoanRequest> read(
      final Scenario scenario) {

    try {
      return Optional.of(loanApproval.show(scenario.getLoanRequestId()));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    }

  }

}
