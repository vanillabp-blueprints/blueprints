package blueprint.workflowmodule.loanapproval;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import blueprint.workflowmodule.Simulator;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * The compliance archive, as far as the test is concerned, plus the one thing that makes
 * this blueprint testable: it is away when the notice first arrives.
 *
 * <p>
 * Without that, the notice would be sent milliseconds after the decision, while the
 * decision is still the newest state, and the test would prove nothing. Turned down once,
 * the notice waits in the outbox, the process books the payout meanwhile, and what the
 * archive is handed at the second attempt is a state the loan approval has left.
 * </p>
 *
 * @see Simulator
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class ComplianceArchiveSimulator extends Simulator implements ComplianceArchive {

  private final Map<String, Aggregate> notices = new ConcurrentHashMap<>();

  private final Set<String> turnedDownOnce = ConcurrentHashMap.newKeySet();

  @Override
  public void store(
      final String event,
      final Aggregate loanApprovalAsItWas) {

    final var loanRequestId = loanApprovalAsItWas.getLoanRequestId();

    record("asked about "
        + loanRequestId);

    if (turnedDownOnce.add(loanRequestId)) {
      throw new IllegalStateException("the compliance archive is not reachable");
    }

    notices.put(loanRequestId, loanApprovalAsItWas);

    record("stored "
        + loanRequestId);

  }

  /**
   * Waits until the archive has a notice about that loan approval, which happens at the
   * second attempt.
   *
   * @param loanRequestId The loan approval the notice is about.
   * @return The loan approval in the state the notice reported.
   */
  public Aggregate awaitNotice(
      final String loanRequestId) {

    awaitInvocation("stored "
        + loanRequestId);

    return notices.get(loanRequestId);

  }

  /**
   * @param loanRequestId The loan approval the notice would be about.
   * @return The notice, if the archive already received one.
   */
  public Optional<Aggregate> notice(
      final String loanRequestId) {

    return Optional.ofNullable(notices.get(loanRequestId));

  }

  @Override
  public void reset() {

    super.reset();
    notices.clear();
    turnedDownOnce.clear();

  }

}
