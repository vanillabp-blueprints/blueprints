package blueprint.workflowmodule.loanapproval;

import java.security.Principal;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CheckDocumentTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;

/**
 * The endpoints the forms of the two user tasks call. Each form has three of them, on the
 * same path:
 *
 * <ul>
 * <li>GET reads what the form shows: the loan request, the draft or the result, and the state
 * of the task.</li>
 * <li>PUT saves a draft. The user may do that as often as they like, or the form does it on
 * its own while the user types. It changes the task and nothing else.</li>
 * <li>POST completes the task with what the user sent. Only now does the result reach the
 * workflow aggregate.</li>
 * </ul>
 *
 * <p>
 * Saving and completing are PUT and POST, not GET. A form sends what a user entered, and that
 * does not belong in a URL. People start and look at a loan approval through
 * {@link ApiController}, which has GET requests only.
 * </p>
 *
 * <p>
 * Every endpoint names the {@link Permission} it needs. A user without it gets 403, whatever
 * the task id is. The module checks this itself, so it does not depend on how the
 * application protects its URLs. The user who completes a task is the user Spring Security
 * logged in, never a value of the request.
 * </p>
 *
 * <p>
 * A task id which is unknown gets 404, a task which is not open any more gets 409. That is
 * the same for every form, so {@code UserTaskExceptionHandler} of the package {@code support}
 * answers it.
 * </p>
 */
@RestController
@RequestMapping("/api/loan-approval/{loanRequestId}")
public class UserTaskFormController {

  @Autowired
  private Service loanApproval;

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, the values of the form and the state of the task.
   */
  @GetMapping("/assess-risk/{taskId}")
  @Secured(Permission.ASSESS_RISK)
  public AssessRiskForm getAssessRiskForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId) {

    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, AssessRiskTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final AssessRiskTaskData data = task.getData();

    return new AssessRiskForm(
        loanRequest.getLoanRequestId(), loanRequest.getAmount(), loanRequest.getCreditRating(), data
            .riskAcceptable(), data.comment(), TaskState.of(task));

  }

  /**
   * Saves a draft of the form "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the form holds right now, may be incomplete.
   */
  @PutMapping("/assess-risk/{taskId}")
  @Secured(Permission.ASSESS_RISK)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void saveAssessRiskForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final AssessRiskInput input) {

    loanApproval.saveRiskAssessmentDraft(loanRequestId, taskId, input.riskAcceptable(), input.comment());

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent at the end.
   * @param user          The user logged in, who completes the task.
   */
  @PostMapping("/assess-risk/{taskId}")
  @Secured(Permission.ASSESS_RISK)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeAssessRisk(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final AssessRiskInput input,
      final Principal user) {

    loanApproval.assessRisk(loanRequestId, taskId, input.riskAcceptable(), input.comment(), user.getName());

  }

  /**
   * What a form "Check document" shows.
   *
   * <p>
   * It also shows the risk assessment, which runs in the other branch of the workflow. It
   * reads it from the aggregate, so it shows the result of the completed task or nothing. A
   * draft somebody saved in the other branch never turns up here.
   * </p>
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The document, the values of the form and the state of the task.
   */
  @GetMapping("/check-document/{taskId}")
  @Secured(Permission.CHECK_DOCUMENT)
  public CheckDocumentForm getCheckDocumentForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId) {

    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, CheckDocumentTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final CheckDocumentTaskData data = task.getData();

    return new CheckDocumentForm(
        loanRequest.getLoanRequestId(), data.documentId(), data.valid(), data.remark(), loanRequest
            .getRiskAcceptable(), TaskState.of(task));

  }

  /**
   * Saves a draft of a form "Check document".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the form holds right now, may be incomplete.
   */
  @PutMapping("/check-document/{taskId}")
  @Secured(Permission.CHECK_DOCUMENT)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void saveCheckDocumentForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final CheckDocumentInput input) {

    loanApproval.saveDocumentCheckDraft(loanRequestId, taskId, input.valid(), input.remark());

  }

  /**
   * Completes one user task "Check document".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent at the end.
   * @param user          The user logged in, who completes the task.
   */
  @PostMapping("/check-document/{taskId}")
  @Secured(Permission.CHECK_DOCUMENT)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeCheckDocument(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final CheckDocumentInput input,
      final Principal user) {

    loanApproval.checkDocument(loanRequestId, taskId, input.valid(), input.remark(), user.getName());

  }

  /**
   * A completion without the value the task is about.
   *
   * @param e What the service found.
   * @return The message, for the form to show.
   */
  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String incomplete(
      final IllegalArgumentException e) {

    return e.getMessage();

  }

  /**
   * The state of a user task, the same for every form.
   *
   * @param createdAt   When the BPMS opened the task.
   * @param updatedAt   When a draft was saved last.
   * @param completedAt When the task was completed or canceled.
   * @param completedBy Who completed it, empty if the workflow canceled it.
   * @param open        Whether somebody can still work on it.
   */
  public record TaskState(
                          OffsetDateTime createdAt,
                          OffsetDateTime updatedAt,
                          OffsetDateTime completedAt,
                          String completedBy,
                          boolean open) {

    static TaskState of(
        final Task task) {

      return new TaskState(
          task.getCreatedAt(), task.getUpdatedAt(), task.getCompletedAt(), task.getCompletedBy(), task.isOpen());

    }

  }

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId  The id of the loan request.
   * @param amount         The amount requested.
   * @param creditRating   The rating of the service task before.
   * @param riskAcceptable The draft, or the result once the task is completed.
   * @param comment        The draft, or the result once the task is completed.
   * @param task           The state of the task.
   */
  public record AssessRiskForm(
                               String loanRequestId,
                               Integer amount,
                               Integer creditRating,
                               Boolean riskAcceptable,
                               String comment,
                               TaskState task) {
  }

  /**
   * What the user entered in the form "Assess risk".
   *
   * @param riskAcceptable Whether the risk is acceptable. Needed to complete, not to save.
   * @param comment        Why.
   */
  public record AssessRiskInput(
                                Boolean riskAcceptable,
                                String comment) {
  }

  /**
   * What a form "Check document" shows.
   *
   * @param loanRequestId  The id of the loan request.
   * @param documentId     The document to check.
   * @param valid          The draft, or the result once the task is completed.
   * @param remark         The draft, or the result once the task is completed.
   * @param riskAcceptable The result of the risk assessment, empty until that task is
   *                       completed.
   * @param task           The state of the task.
   */
  public record CheckDocumentForm(
                                  String loanRequestId,
                                  String documentId,
                                  Boolean valid,
                                  String remark,
                                  Boolean riskAcceptable,
                                  TaskState task) {
  }

  /**
   * What the user entered in a form "Check document".
   *
   * @param valid  Whether the document is in order. Needed to complete, not to save.
   * @param remark What the user noted.
   */
  public record CheckDocumentInput(
                                   Boolean valid,
                                   String remark) {
  }

}
