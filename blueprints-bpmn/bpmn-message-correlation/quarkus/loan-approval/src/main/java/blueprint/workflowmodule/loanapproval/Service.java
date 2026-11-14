package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, {@code loanRequested} rather than "start the process", and that class
 * decides what this means for the BPMN. The other direction runs through
 * {@link WorkflowTaskHandler}, which calls the methods below when the process reaches a
 * task.
 * </p>
 *
 * <p>
 * Both directions meet here, and that is the point: this is the one class describing the
 * use case, and it does so without naming a single BPMN element.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 *
 * <p>
 * No method here throws a {@code TaskException}. One which does, so the process can react,
 * needs {@code @Transactional(dontRollbackOn = TaskException.class)}: VanillaBP commits its
 * transaction for that exception, and a plain {@code @Transactional} would mark it for
 * rollback.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Transactional
public class Service {

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  public void request(
      final String loanRequestId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Rates a loan request. A real application would ask a rating service here; what matters
   * for the blueprint is where this code sits: in the business service, not in the
   * {@code @WorkflowTask} method which happens to trigger it.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.ratingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}. The workflow now waits for the signed contract:"
            + "\n  Signed -> http://localhost:8080/api/loan-approval/{}/contract-signed?signedBy=Jane%20Doe",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval.getLoanRequestId());

  }

  /**
   * The contract came back signed. This is the message the workflow waits for, and it
   * arrives at the API rather than at the BPMS.
   *
   * <p>
   * The order of the two statements is the point: whatever the message carries is written
   * onto the aggregate FIRST, and only then is the message correlated. The BPMS learns
   * the name of the message and nothing else, so everything downstream - a gateway, a
   * later task, a report - reads the aggregate instead of a payload nobody can query.
   * </p>
   *
   * <p>
   * The early return is what makes a message arriving twice harmless. Without a
   * correlation id VanillaBP does not deduplicate, deliberately: the same message may
   * legitimately arrive several times over a workflow's lifetime, and only the
   * application knows which case it is in.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param signedBy      Who signed, taken from the message.
   */
  public void contractSigned(
      final String loanRequestId,
      final String signedBy) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (loanApproval.getContractSignedBy() != null) {

      log.info(
          "The contract of loan approval '{}' was signed already, nothing to correlate",
          loanRequestId);
      return;

    }

    loanApproval.setContractSignedBy(signedBy);

    workflow.contractSigned(loanApproval);

    log.info(
        "The contract of loan approval '{}' was signed by '{}'",
        loanRequestId,
        signedBy);

  }

  /**
   * Pays the loan out, which is what the service task behind the message event triggers.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void payOut(
      final Aggregate loanApproval) {

    loanApproval.setPaidOut(true);

    log.info(
        "Loan approval '{}' was paid out to '{}'",
        loanApproval.getLoanRequestId(),
        loanApproval.getContractSignedBy());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
