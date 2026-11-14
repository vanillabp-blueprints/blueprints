package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, and that class decides what this means for the BPMN. The other direction runs
 * through {@link WorkflowTaskHandler}, which calls the methods below when the process reaches
 * a user task.
 * </p>
 *
 * <p>
 * Each user task has three methods here. The first keeps the id of the task when it opens. The
 * second gives a form what it shows. The third takes what the user entered and completes the
 * task. A form knows the id of its user task and nothing else, so the id is how the second and
 * the third find the loan request.
 * </p>
 *
 * <p>
 * What a user enters is written to the aggregate once, when the task is completed. A form
 * which wants to keep a draft while the user is still typing needs a store of its own. The
 * aggregate holds results, not drafts.
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
 * needs {@code @Transactional(noRollbackFor = TaskException.class)}: VanillaBP commits its
 * transaction for that exception, and a plain {@code @Transactional} would mark it for
 * rollback.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@Transactional
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

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
   * Somebody has to assess the risk: the process created the user task and reports its id.
   * The cockpit adapter reports the same task to the Business Cockpit, where a user opens its
   * form.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task just created.
   */
  public void riskAssessmentOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setAssessRiskTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a risk assessment. Its form reads"
            + "\n  http://localhost:8080/loan-approval/api/user-tasks/{}/assess-risk",
        loanApproval.getLoanRequestId(),
        taskId);

  }

  /**
   * What the form "Assess risk" shows.
   *
   * @param taskId The id of the user task.
   * @return The loan approval, if the task is open.
   */
  public Optional<Aggregate> riskAssessment(
      final String taskId) {

    return loanApprovals.findByAssessRiskTaskId(taskId);

  }

  /**
   * The risk was assessed. This is the answer the process waits for. It arrives from the form,
   * not from the BPMS.
   *
   * @param taskId         The id of the user task being answered.
   * @param riskAssessment What the user entered.
   * @return False if no user task "Assess risk" is open under this id.
   */
  public boolean assessRisk(
      final String taskId,
      final String riskAssessment) {

    final var found = loanApprovals.findByAssessRiskTaskId(taskId);
    if (found.isEmpty()) {
      return false;
    }
    final var loanApproval = found.get();

    loanApproval.setRiskAssessment(riskAssessment);

    workflow.riskAssessed(loanApproval, taskId);

    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setAssessRiskTaskId(null);

    log.info(
        "Risk of loan approval '{}' was assessed: {}",
        loanApproval.getLoanRequestId(),
        riskAssessment);

    return true;

  }

  /**
   * Somebody has to check the documents: the process created the second user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task just created.
   */
  public void documentsCheckOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setCheckDocumentsTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a check of the documents. Its form reads"
            + "\n  http://localhost:8080/loan-approval/api/user-tasks/{}/check-documents",
        loanApproval.getLoanRequestId(),
        taskId);

  }

  /**
   * What the form "Check documents" shows.
   *
   * @param taskId The id of the user task.
   * @return The loan approval, if the task is open.
   */
  public Optional<Aggregate> documentsCheck(
      final String taskId) {

    return loanApprovals.findByCheckDocumentsTaskId(taskId);

  }

  /**
   * The documents were checked.
   *
   * @param taskId        The id of the user task being answered.
   * @param documentsNote What the user entered.
   * @return False if no user task "Check documents" is open under this id.
   */
  public boolean checkDocuments(
      final String taskId,
      final String documentsNote) {

    final var found = loanApprovals.findByCheckDocumentsTaskId(taskId);
    if (found.isEmpty()) {
      return false;
    }
    final var loanApproval = found.get();

    loanApproval.setDocumentsNote(documentsNote);

    workflow.documentsChecked(loanApproval, taskId);

    loanApproval.setCheckDocumentsTaskId(null);

    log.info(
        "Documents of loan approval '{}' were checked: {}",
        loanApproval.getLoanRequestId(),
        documentsNote);

    return true;

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
