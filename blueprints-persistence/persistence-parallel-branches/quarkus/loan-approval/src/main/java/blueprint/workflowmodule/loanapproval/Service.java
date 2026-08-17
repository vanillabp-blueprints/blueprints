package blueprint.workflowmodule.loanapproval;

import java.time.OffsetDateTime;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.DocumentCheck;
import blueprint.workflowmodule.loanapproval.model.PartnerApproval;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * Two of the methods below run at the same time. {@link #recordDocuments} is called by the
 * BPMS in the transaction VanillaBP owns, {@link #approvePartnerRequest} by the API in a
 * transaction this application opens. Each of them writes the entity of its own branch and
 * touches nothing the other one writes, which is what keeps the later commit from throwing
 * away the earlier one.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow and answering a task have to run in a transaction. It is deliberately
 * absent from the methods a task handler calls: VanillaBP already runs a task in a
 * transaction it owns, and a transaction declared here would break the guarantees that come
 * with it.
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
  DocumentsClient documents;

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
   * Rates a loan request. This runs before the process splits, so it is the last method
   * writing an attribute of the aggregate itself while a single token exists.
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
   * The partner was asked and the task waiting for the answer is open. What is stored is
   * the branch's own record, including the task id: an id on the aggregate would be
   * overwritten by the other branch, which reads that row before this method runs.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open task.
   */
  public void partnerApprovalRequested(
      final Aggregate loanApproval,
      final String taskId) {

    if (loanApproval.getPartnerApproval() != null) {
      // a remote BPMS may deliver the same task twice; the state of the aggregate decides
      return;
    }

    loanApproval.setPartnerApproval(PartnerApproval
        .builder()
        .taskId(taskId)
        .build());

    log.info(
        "Loan approval '{}' waits for the partner. Approve it with:"
            + "\n  http://localhost:8080/api/loan-approval/{}/approve?approvedBy=partner",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId());

  }

  /**
   * The partner answered, which completes the open task. This is the transaction of the
   * application, and it runs while the other branch is inside the transaction of its task.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param approvedBy    Who approved.
   */
  @Transactional
  public void approvePartnerRequest(
      final String loanRequestId,
      final String approvedBy) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    final var partnerApproval = loanApproval.getPartnerApproval();
    if ((partnerApproval == null) || (partnerApproval.getTaskId() == null)) {
      throw new IllegalStateException("Loan approval '"
          + loanRequestId
          + "' is not waiting for the partner");
    }

    partnerApproval.setApprovedBy(approvedBy);
    partnerApproval.setApprovedAt(OffsetDateTime.now());
    loanApprovals.persist(loanApproval);

    workflow.partnerApproved(loanApproval, partnerApproval.getTaskId());

    log.info("The partner approved loan approval '{}'", loanRequestId);

  }

  /**
   * The documents were asked for and this branch waits for them. What is stored is the
   * branch's own record, including the id of its open task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open task.
   */
  public void documentsRequested(
      final Aggregate loanApproval,
      final String taskId) {

    if (loanApproval.getDocumentCheck() != null) {
      // a remote BPMS may deliver the same task twice; the state of the aggregate decides
      return;
    }

    loanApproval.setDocumentCheck(DocumentCheck
        .builder()
        .taskId(taskId)
        .build());

    log.info(
        "Loan approval '{}' waits for the documents. They arrive with:"
            + "\n  http://localhost:8080/api/loan-approval/{}/documents-ready",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId());

  }

  /**
   * The document service says the documents are there, which completes the open task. The
   * branch then reads them in the transaction VanillaBP owns.
   *
   * @param loanRequestId The natural id of the loan request.
   */
  @Transactional
  public void documentsReady(
      final String loanRequestId) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    final var documentCheck = loanApproval.getDocumentCheck();
    if ((documentCheck == null) || (documentCheck.getTaskId() == null)) {
      throw new IllegalStateException("Loan approval '"
          + loanRequestId
          + "' is not waiting for documents");
    }

    workflow.documentsDelivered(loanApproval, documentCheck.getTaskId());

  }

  /**
   * Reads the documents and writes what came back into the record of this branch. This runs
   * in the transaction VanillaBP owns, and it is the branch which may be committing while
   * the API answers the other one.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void recordDocuments(
      final Aggregate loanApproval) {

    final var documentCheck = loanApproval.getDocumentCheck();
    if (documentCheck.getDocumentsReceived() != null) {
      return;
    }

    final var received = documents.collect(loanApproval.getLoanRequestId());

    documentCheck.setDocumentsReceived(received);
    documentCheck.setCollectedAt(OffsetDateTime.now());

    log.info(
        "Loan approval '{}' collected {} document(s)",
        loanApproval.getLoanRequestId(),
        received);

  }

  /**
   * Both branches are done, so a single token is left and writing the aggregate itself is
   * safe again.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void informCustomer(
      final Aggregate loanApproval) {

    loanApproval.setCustomerInformed(Boolean.TRUE);

    log.info(
        "The customer of loan approval '{}' was informed: approved by {}, {} document(s)",
        loanApproval.getLoanRequestId(),
        loanApproval
            .getPartnerApproval()
            .getApprovedBy(),
        loanApproval
            .getDocumentCheck()
            .getDocumentsReceived());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  @Transactional
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
