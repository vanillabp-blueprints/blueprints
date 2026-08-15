package blueprint.workflowmodule.loanapproval;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.WorkflowHistory;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * The three viewing methods are the exception that proves the rule. They do not describe
 * the business case, they answer "show me this workflow", which is what a UI asks for. They
 * still go through {@link Workflow} rather than touching VanillaBP here, and they are the
 * only methods of this class a reader will recognise as being about a process at all.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls which CHANGE
 * something. Reading a diagram or a history changes nothing, persists nothing and needs no
 * transaction. It is deliberately absent from the methods a task handler calls: VanillaBP
 * already runs a task in a transaction it owns.
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
   * Rates a loan request.
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
   * The workflow reached the task it waits at. Remembering its id is what makes the wait
   * answerable later.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the task.
   */
  public void awaitPartnerApproval(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setPartnerApprovalTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for the partner. Look at it while it waits:"
            + " http://localhost:8080/api/loan-approval/{}/history",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId());
    log.info(
        "Approve -> http://localhost:8080/api/loan-approval/{}/approve",
        loanApproval.getLoanRequestId());

  }

  /**
   * The partner approved, which moves the workflow on.
   *
   * @param loanRequestId The natural id of the loan request.
   */
  @Transactional
  public void partnerApproved(
      final String loanRequestId) {

    final var loanApproval = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown loan request '"
                + loanRequestId
                + "'"));

    workflow.partnerApproved(loanApproval, loanApproval.getPartnerApprovalTaskId());

    log.info("Partner approved loan approval '{}'", loanRequestId);

  }

  /**
   * Decides on the loan, after the partner answered.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void decideOnLoan(
      final Aggregate loanApproval) {

    loanApproval.setDecision(loanApproval.getCreditRating() >= properties.getMinimumRating()
        ? "approved"
        : "rejected");

    log.info(
        "Loan approval '{}' was {}. Look at what it did:"
            + " http://localhost:8080/api/loan-approval/{}/history",
        loanApproval.getLoanRequestId(),
        loanApproval.getDecision(),
        loanApproval.getLoanRequestId());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

  /**
   * The process definitions of a loan approval's workflow.
   *
   * @param loanRequestId  The natural id of the loan request.
   * @param historyContext The context of a call activity, or {@code null}.
   * @return The definitions the workflow uses.
   */
  public List<ProcessDefinition> getProcessDefinitions(
      final String loanRequestId,
      final String historyContext) {

    return workflow.processDefinitions(loadOrFail(loanRequestId), historyContext);

  }

  /**
   * The BPMN XML of one process definition.
   *
   * @param processDefinitionId The id of the definition, as reported.
   * @return The BPMN XML.
   */
  public InputStream getBpmnXml(
      final String processDefinitionId) {

    return workflow.bpmnXml(processDefinitionId);

  }

  /**
   * What a loan approval's workflow has done so far.
   *
   * @param loanRequestId  The natural id of the loan request.
   * @param historyContext The context of a call activity, or {@code null}.
   * @return The history.
   */
  public WorkflowHistory getWorkflowHistory(
      final String loanRequestId,
      final String historyContext) {

    return workflow.history(loadOrFail(loanRequestId), historyContext);

  }

  private Aggregate loadOrFail(
      final String loanRequestId) {

    return loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown loan request '"
                + loanRequestId
                + "'"));

  }

}
