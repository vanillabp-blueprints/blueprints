package blueprint.workflowmodule.loanapproval;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.UserTaskFormsApi;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentForm;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentInput;
import blueprint.workflowmodule.loanapproval.api.model.TaskState;
import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CheckDocumentTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import io.vanillabp.cockpit.support.usertask.BcUserTaskExceptionHandler;

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
 * the same for every form of every module, so the Spring Boot support of the Business Cockpit
 * brings both exceptions and {@link BcUserTaskExceptionHandler}, which answers them. Nothing
 * registers the handler on its own, so the controller imports it.
 * </p>
 *
 * <p>
 * The paths and the records of the requests and answers come from {@link UserTaskFormsApi}, the
 * interface the build generates from {@code src/main/resources/openapi/user-task-forms.yaml}.
 * This class only implements it, and adds the permission of each endpoint.
 * </p>
 */
@RestController
@Import(BcUserTaskExceptionHandler.class)
public class UserTaskFormController implements UserTaskFormsApi {

  @Autowired
  private Service loanApproval;

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, the values of the form and the state of the task.
   */
  @Override
  @Secured(Permission.ASSESS_RISK)
  public AssessRiskForm getAssessRiskForm(
      final String loanRequestId,
      final String taskId) {

    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, AssessRiskTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final AssessRiskTaskData data = task.getData();

    return new AssessRiskForm(
        loanRequest.getLoanRequestId(), loanRequest.getAmount(), loanRequest.getCreditRating(), data
            .riskAcceptable(), data.comment(), stateOf(task));

  }

  /**
   * Saves a draft of the form "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the form holds right now, may be incomplete.
   */
  @Override
  @Secured(Permission.ASSESS_RISK)
  public void saveAssessRiskForm(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    loanApproval.saveRiskAssessmentDraft(loanRequestId, taskId, input.riskAcceptable(), input.comment());

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent at the end. The user logged in completes the task.
   */
  @Override
  @Secured(Permission.ASSESS_RISK)
  public void completeAssessRisk(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    loanApproval.assessRisk(loanRequestId, taskId, input.riskAcceptable(), input.comment(), whoIsLoggedIn());

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
  @Override
  @Secured(Permission.CHECK_DOCUMENT)
  public CheckDocumentForm getCheckDocumentForm(
      final String loanRequestId,
      final String taskId) {

    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, CheckDocumentTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final CheckDocumentTaskData data = task.getData();

    return new CheckDocumentForm(
        loanRequest.getLoanRequestId(), data.documentId(), data.valid(), data.remark(), loanRequest
            .getRiskAcceptable(), stateOf(task));

  }

  /**
   * Saves a draft of a form "Check document".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the form holds right now, may be incomplete.
   */
  @Override
  @Secured(Permission.CHECK_DOCUMENT)
  public void saveCheckDocumentForm(
      final String loanRequestId,
      final String taskId,
      final CheckDocumentInput input) {

    loanApproval.saveDocumentCheckDraft(loanRequestId, taskId, input.valid(), input.remark());

  }

  /**
   * Completes one user task "Check document".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent at the end. The user logged in completes the task.
   */
  @Override
  @Secured(Permission.CHECK_DOCUMENT)
  public void completeCheckDocument(
      final String loanRequestId,
      final String taskId,
      final CheckDocumentInput input) {

    loanApproval.checkDocument(loanRequestId, taskId, input.valid(), input.remark(), whoIsLoggedIn());

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
   * The user Spring Security logged in for this request. The generated interface knows nothing
   * about security, so the name is taken from the security context and never from the request.
   *
   * @return The name of the user.
   */
  private static String whoIsLoggedIn() {

    return SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getName();

  }

  /**
   * The state of a user task, the same for every form.
   *
   * @param task The user task.
   * @return When it was opened, saved and completed, by whom, and whether it is still open.
   */
  private static TaskState stateOf(
      final Task task) {

    return new TaskState(
        task.getCreatedAt(), task.getUpdatedAt(), task.getCompletedAt(), task.getCompletedBy(), task.isOpen());

  }

}
