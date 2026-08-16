package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.service.TaskException;
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
 * Note where {@code @Transactional} sits. It is on the method the API calls, because
 * starting a workflow has to run in a transaction. It is deliberately absent from the
 * methods a task handler calls: VanillaBP already runs a task in a transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared here would roll back instead and throw away what the handler wrote for the
 * process to react to. VanillaBP sees the transaction it can no longer commit and fails the
 * task naming it, so the mistake shows up rather than costing data.
 * </p>
 */
@Slf4j
@ApplicationScoped
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
  @Transactional
  public void initiateLoanApproval(
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
   * The BPMN error raised when the documents are incomplete. The same string is the error
   * code of the error boundary event in the model, and there is no second place it is
   * written down.
   */
  public static final String DOCUMENTS_MISSING = "documents-missing";

  /**
   * Checks the documents of a loan request. This is the one place in this blueprint where
   * business code decides how the process continues: a {@link TaskException} ends the task
   * with a BPMN error, and whatever was written before it is committed nevertheless.
   *
   * <p>
   * There is no counterpart for an escalation. Business code can say "this cannot be
   * completed"; that something should be reported upwards while work carries on is decided
   * by the model, and the escalation of this process is thrown there.
   * </p>
   *
   * @param loanApproval The loan approval whose documents are checked.
   */
  public void verifyDocuments(
      final Aggregate loanApproval) {

    if (loanApproval.getAmount() >= properties.documentsRequiredFrom()) {
      return;
    }

    log.info(
        "Loan approval '{}' has incomplete documents",
        loanApproval.getLoanRequestId());

    throw new TaskException(DOCUMENTS_MISSING);

  }

  /**
   * Asks the customer for the missing documents, which is what the task behind the error
   * boundary event triggers.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void requestDocuments(
      final Aggregate loanApproval) {

    loanApproval.setDocumentsRequested(true);

    log.info(
        "Documents were requested for loan approval '{}'",
        loanApproval.getLoanRequestId());

  }

  /**
   * Signs the contract, the last task inside the subprocess.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void signContract(
      final Aggregate loanApproval) {

    loanApproval.setContractSigned(true);

    log.info("The contract of loan approval '{}' was signed", loanApproval.getLoanRequestId());

  }

  /**
   * Informs the supervisor, on the branch the escalation leads to. It runs BESIDE the
   * subprocess, which is what a non-interrupting boundary event means.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void informSupervisor(
      final Aggregate loanApproval) {

    loanApproval.setSupervisorInformed(true);

    log.info(
        "The supervisor was informed about loan approval '{}'",
        loanApproval.getLoanRequestId());

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
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
