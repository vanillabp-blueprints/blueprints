package blueprint.workflowmodule.loanapproval.overdraft;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.creditcheck.CreditChecks;
import blueprint.workflowmodule.loanapproval.overdraft.model.Aggregate;
import blueprint.workflowmodule.loanapproval.overdraft.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the overdraft request. It uses the credit check exactly as the loan
 * approval does, through {@link CreditChecks}. Only what it does with the score is its own.
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Transactional
public class Service {

  /**
   * How the overdraft request names itself when it asks for a credit check. The credit check
   * hands it back with the result.
   */
  public static final String CREDIT_CHECK_REQUESTER = "overdraft";

  @Inject
  AggregateRepository overdrafts;

  @Inject
  Workflow workflow;

  @Inject
  CreditChecks creditChecks;

  /**
   * A customer asks for an overdraft on the current account.
   *
   * @param overdraftId    The natural id of the overdraft request.
   * @param requestedLimit The limit asked for.
   */
  public void request(
      final String overdraftId,
      final int requestedLimit) {

    final var overdraft = Aggregate
        .builder()
        .overdraftId(overdraftId)
        .requestedLimit(requestedLimit)
        .build();

    workflow.overdraftRequested(overdraft);

    log.info("Overdraft request '{}' started", overdraftId);

  }

  /**
   * Asks for a credit check, in the same transaction.
   *
   * @param overdraft The overdraft request which needs the credit check.
   */
  public void requestCreditCheck(
      final Aggregate overdraft) {

    final var creditCheckId = creditChecks.request(
        CREDIT_CHECK_REQUESTER,
        overdraft.getOverdraftId(),
        overdraft.getRequestedLimit());

    overdraft.setCreditCheckId(creditCheckId);

    log.info(
        "Overdraft request '{}' waits for credit check '{}'",
        overdraft.getOverdraftId(),
        creditCheckId);

  }

  /**
   * The credit check reported its result. The score is written onto the aggregate FIRST, and
   * only then is the message correlated. A result which arrives twice changes nothing.
   *
   * @param overdraftId The natural id of the overdraft request.
   * @param score       The score the credit check reported.
   */
  public void creditChecked(
      final String overdraftId,
      final int score) {

    final var overdraft = overdrafts
        .findByIdOptional(overdraftId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown overdraft request '"
            + overdraftId
            + "'"));

    if (overdraft.getCreditScore() != null) {

      log.info(
          "The credit of overdraft request '{}' was checked already, nothing to correlate",
          overdraftId);
      return;

    }

    overdraft.setCreditScore(score);

    workflow.creditChecked(overdraft);

    log.info(
        "The credit check of overdraft request '{}' reported a score of {}",
        overdraftId,
        score);

  }

  /**
   * Sets the limit. The overdraft gets the share of the requested limit which the score says,
   * so a score of 70 grants 70 percent.
   *
   * @param overdraft The workflow's aggregate.
   */
  public void setLimit(
      final Aggregate overdraft) {

    final var grantedLimit = overdraft.getRequestedLimit() * overdraft.getCreditScore() / 100;

    overdraft.setGrantedLimit(grantedLimit);

    log.info(
        "Overdraft request '{}' was granted a limit of {}",
        overdraft.getOverdraftId(),
        grantedLimit);

  }

  /**
   * The state of an overdraft request, as far as the process has come.
   *
   * @param overdraftId The natural id of the overdraft request.
   * @return The overdraft request, if it exists.
   */
  public Optional<Aggregate> get(
      final String overdraftId) {

    return overdrafts.findByIdOptional(overdraftId);

  }

}
