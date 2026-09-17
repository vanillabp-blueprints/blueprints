package blueprint.workflowmodule.loanapproval;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * The stand-in for the compliance archive, so the blueprint runs without one.
 *
 * <p>
 * It writes the notice into the log, and it turns the first attempt down. That refusal is
 * arranged: an archive which answers right away would receive the decision while the
 * decision is still the newest state, and there would be nothing to see. Turned down
 * once, the notice waits in the outbox until the retry, the process books the payout
 * meanwhile, and the line it finally writes shows the loan approval without the payout.
 * </p>
 *
 * <p>
 * Nothing about that is unusual for an outbox. A receiver is away now and then, and the
 * entry waits - minutes on a bad day, days when somebody is on holiday. This class only
 * makes the ordinary case happen on the first run.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class LocalComplianceArchive implements ComplianceArchive {

  private final Set<String> turnedDownOnce = ConcurrentHashMap.newKeySet();

  @Override
  public void store(
      final String event,
      final Aggregate loanApprovalAsItWas) {

    final var loanRequestId = loanApprovalAsItWas.getLoanRequestId();

    if (turnedDownOnce.add(event
        + "|"
        + loanRequestId)) {
      log.info(
          "The compliance archive does not answer about loan approval '{}'. VanillaBP keeps the "
              + "notice and tries again.",
          loanRequestId);
      throw new IllegalStateException("the compliance archive is not reachable");
    }

    log.info(
        "The compliance archive recorded '{}' of loan approval '{}': {}",
        event,
        loanRequestId,
        loanApprovalAsItWas);

  }

}
