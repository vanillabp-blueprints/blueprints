package blueprint.workflowmodule.loanapproval.creditcheck;

import java.util.Optional;
import java.util.UUID;

import blueprint.workflowmodule.loanapproval.creditcheck.model.Aggregate;
import blueprint.workflowmodule.loanapproval.creditcheck.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the credit check, the process other processes reuse.
 *
 * <p>
 * Nothing here knows the processes which use it. A request comes in through
 * {@link CreditChecks}, and the result goes out as a {@link CreditChecked} event. Whoever
 * asked listens for it.
 * </p>
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
public class Service implements CreditChecks {

  @Inject
  AggregateRepository creditChecks;

  @Inject
  Workflow workflow;

  @Inject
  Event<CreditChecked> events;

  @Override
  public String request(
      final String requestedBy,
      final String caseId,
      final int amount) {

    final var creditCheck = Aggregate
        .builder()
        .creditCheckId(UUID.randomUUID().toString())
        .requestedBy(requestedBy)
        .caseId(caseId)
        .amount(amount)
        .build();

    workflow.creditCheckRequested(creditCheck);

    log.info(
        "Credit check '{}' started for {} '{}'",
        creditCheck.getCreditCheckId(),
        requestedBy,
        caseId);

    return creditCheck.getCreditCheckId();

  }

  /**
   * Asks the credit agency. A real application would call the agency here. This one logs the
   * URL at which the answer arrives, so a person can play the agency in a browser.
   *
   * @param creditCheck The workflow's aggregate.
   */
  public void askCreditAgency(
      final Aggregate creditCheck) {

    creditCheck.setAgencyAsked(true);

    log.info(
        "Credit check '{}' waits for the agency:"
            + "\n  Answer -> http://localhost:8080/loan-approval/api/credit-checks/{}/agency-answered?score=70",
        creditCheck.getCreditCheckId(),
        creditCheck.getCreditCheckId());

  }

  /**
   * The agency answered. The score is written onto the aggregate FIRST, and only then is the
   * message correlated. An answer which arrives twice changes nothing.
   *
   * @param creditCheckId The natural id of the credit check.
   * @param score         The score the agency gave.
   */
  public void agencyAnswered(
      final String creditCheckId,
      final int score) {

    final var creditCheck = creditChecks
        .findByIdOptional(creditCheckId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown credit check '"
            + creditCheckId
            + "'"));

    if (creditCheck.getScore() != null) {

      log.info(
          "The agency answered credit check '{}' already, nothing to correlate",
          creditCheckId);
      return;

    }

    creditCheck.setScore(score);

    workflow.agencyAnswered(creditCheck);

    log.info(
        "The agency gave credit check '{}' a score of {}",
        creditCheckId,
        score);

  }

  /**
   * Reports the result to whoever asked. The event is delivered at once, in this transaction.
   * So the result and the message it causes in the waiting process are committed together, or
   * not at all.
   *
   * @param creditCheck The workflow's aggregate.
   */
  public void reportResult(
      final Aggregate creditCheck) {

    events.fire(
        new CreditChecked(creditCheck.getRequestedBy(), creditCheck.getCaseId(), creditCheck.getScore()));

    creditCheck.setReported(true);

    log.info(
        "Credit check '{}' reported a score of {} to {} '{}'",
        creditCheck.getCreditCheckId(),
        creditCheck.getScore(),
        creditCheck.getRequestedBy(),
        creditCheck.getCaseId());

  }

  /**
   * The state of a credit check, as far as the process has come.
   *
   * @param creditCheckId The natural id of the credit check.
   * @return The credit check, if it exists.
   */
  public Optional<Aggregate> get(
      final String creditCheckId) {

    return creditChecks.findByIdOptional(creditCheckId);

  }

}
