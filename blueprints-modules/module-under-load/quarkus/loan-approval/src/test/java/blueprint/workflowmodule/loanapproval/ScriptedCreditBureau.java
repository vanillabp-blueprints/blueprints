package blueprint.workflowmodule.loanapproval;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The credit bureau of the module test. It answers a moment later, like the real one, except
 * about the loan requests the test told it to stay silent about.
 *
 * <p>
 * In the application, the load driver plays the bureau. The module test has no driver, so it
 * brings this one.
 * </p>
 */
@ApplicationScoped
public class ScriptedCreditBureau implements CreditBureau {

  /** The score this bureau gives everybody. */
  static final int SCORE = 70;

  private final Set<String> silentAbout = ConcurrentHashMap.newKeySet();

  @Inject
  Service loanApproval;

  /**
   * Makes the bureau stay silent about one loan request.
   *
   * @param loanRequestId The natural id of the loan request.
   */
  public void staySilentAbout(
      final String loanRequestId) {

    silentAbout.add(loanRequestId);

  }

  @Override
  public void requestScore(
      final String loanRequestId,
      final int amount) {

    if (silentAbout.contains(loanRequestId)) {
      return;
    }
    CompletableFuture.runAsync(
        () -> loanApproval.creditScoreReceived(loanRequestId, SCORE),
        CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS));

  }

}
