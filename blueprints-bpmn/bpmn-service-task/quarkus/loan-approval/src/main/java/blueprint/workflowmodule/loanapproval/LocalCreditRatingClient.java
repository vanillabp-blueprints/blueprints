package blueprint.workflowmodule.loanapproval;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * A stand-in for the real rating provider, so the blueprint runs without one. Replace it
 * by an HTTP client, a queue producer or whatever your provider speaks.
 *
 * <p>
 * It fails the first request for every loan request if
 * {@code loan-approval.fail-first-rating-attempt} is on, which is how the retry of the
 * BPMS becomes visible in the log while clicking through the process. Switch it off and
 * the process runs straight through.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class LocalCreditRatingClient implements CreditRatingClient {

  @Inject
  LoanApprovalProperties properties;

  private final Set<String> alreadyFailed = ConcurrentHashMap.newKeySet();

  @Override
  public int rate(
      final String provider,
      final String loanRequestId,
      final int amount) {

    if (properties.failFirstRatingAttempt() && alreadyFailed.add(loanRequestId)) {

      log.info(
          "Rating provider '{}' is not answering for loan approval '{}'. The BPMS will retry the task.",
          provider,
          loanRequestId);

      throw new CreditRatingUnavailableException(provider);

    }

    return Math.min(
        properties.ratingScale(),
        amount / 100);

  }

}
