package blueprint.workflowmodule.loanapproval;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.DocumentCheck;
import blueprint.workflowmodule.loanapproval.model.PartnerApproval;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * Two of the methods below run at the same time. {@link #collectDocuments} is called by the
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
@org.springframework.stereotype.Service
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private DocumentsClient documents;

  @Autowired
  private LoanApprovalProperties properties;

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
        properties.getRatingScale(),
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
        .findById(loanRequestId)
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
    loanApprovals.save(loanApproval);

    workflow.partnerApproved(loanApproval, partnerApproval.getTaskId());

    log.info("The partner approved loan approval '{}'", loanRequestId);

  }

  /**
   * Collects the documents, which is the other branch. It asks a surrounding system and
   * writes what came back into the record of this branch, while the partner branch may be
   * answered at the very same moment.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void collectDocuments(
      final Aggregate loanApproval) {

    if (loanApproval.getDocumentCheck() != null) {
      return;
    }

    final var received = documents.collect(loanApproval.getLoanRequestId());

    loanApproval.setDocumentCheck(DocumentCheck
        .builder()
        .documentsReceived(received)
        .collectedAt(OffsetDateTime.now())
        .build());

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

    return loanApprovals.findById(loanRequestId);

  }

}
