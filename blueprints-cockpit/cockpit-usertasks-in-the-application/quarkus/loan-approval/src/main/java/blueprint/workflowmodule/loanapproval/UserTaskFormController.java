package blueprint.workflowmodule.loanapproval;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

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
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;

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
 * application protects its URLs. The user who completes a task is the user Quarkus logged in,
 * never a value of the request.
 * </p>
 *
 * <p>
 * A task id which is unknown gets 404, a task which is not open any more gets 409. That is
 * the same for every form of every module, so the support library of the Business Cockpit
 * brings both exceptions and the mappers which answer them. Quarkus finds the mappers in the
 * index of that library, so the controller needs nothing for it.
 * </p>
 *
 * <p>
 * The paths and the classes of the requests and answers come from {@link UserTaskFormsApi}, the
 * interface the build generates from {@code src/main/resources/openapi/user-task-forms.yaml}.
 * This class only implements it, and adds the permission of each endpoint.
 * </p>
 */
@ApplicationScoped
public class UserTaskFormController implements UserTaskFormsApi {

  @Inject
  Service loanApproval;

  /** The user Quarkus logged in for this request. */
  @Inject
  SecurityIdentity identity;

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, the values of the form and the state of the task.
   */
  @Override
  @RolesAllowed(Permission.ASSESS_RISK)
  public AssessRiskForm getAssessRiskForm(
      final String loanRequestId,
      final String taskId) {

    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, AssessRiskTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final AssessRiskTaskData data = task.getData();

    return new AssessRiskForm()
        .loanRequestId(loanRequest.getLoanRequestId())
        .amount(loanRequest.getAmount())
        .creditRating(loanRequest.getCreditRating())
        .riskAcceptable(data.riskAcceptable())
        .comment(data.comment())
        .task(stateOf(task));

  }

  /**
   * Saves a draft of the form "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the form holds right now, may be incomplete.
   */
  @Override
  @RolesAllowed(Permission.ASSESS_RISK)
  public void saveAssessRiskForm(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    loanApproval.saveRiskAssessmentDraft(loanRequestId, taskId, input.getRiskAcceptable(), input.getComment());

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent at the end. The user logged in completes the task.
   */
  @Override
  @RolesAllowed(Permission.ASSESS_RISK)
  public void completeAssessRisk(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    loanApproval.assessRisk(loanRequestId, taskId, input.getRiskAcceptable(), input.getComment(), whoIsLoggedIn());

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
  @RolesAllowed(Permission.CHECK_DOCUMENT)
  public CheckDocumentForm getCheckDocumentForm(
      final String loanRequestId,
      final String taskId) {

    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, CheckDocumentTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final CheckDocumentTaskData data = task.getData();

    return new CheckDocumentForm()
        .loanRequestId(loanRequest.getLoanRequestId())
        .documentId(data.documentId())
        .valid(data.valid())
        .remark(data.remark())
        .riskAcceptable(loanRequest.getRiskAcceptable())
        .task(stateOf(task));

  }

  /**
   * Saves a draft of a form "Check document".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the form holds right now, may be incomplete.
   */
  @Override
  @RolesAllowed(Permission.CHECK_DOCUMENT)
  public void saveCheckDocumentForm(
      final String loanRequestId,
      final String taskId,
      final CheckDocumentInput input) {

    loanApproval.saveDocumentCheckDraft(loanRequestId, taskId, input.getValid(), input.getRemark());

  }

  /**
   * Completes one user task "Check document".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent at the end. The user logged in completes the task.
   */
  @Override
  @RolesAllowed(Permission.CHECK_DOCUMENT)
  public void completeCheckDocument(
      final String loanRequestId,
      final String taskId,
      final CheckDocumentInput input) {

    loanApproval.checkDocument(loanRequestId, taskId, input.getValid(), input.getRemark(), whoIsLoggedIn());

  }

  /**
   * A completion without the value the task is about.
   *
   * @param e What the service found.
   * @return The message, for the form to show.
   */
  @ServerExceptionMapper
  public RestResponse<String> incomplete(
      final IllegalArgumentException e) {

    return RestResponse.status(Status.BAD_REQUEST, e.getMessage());

  }

  /**
   * The user Quarkus logged in for this request. The generated interface knows nothing about
   * security, so the name is taken from the security identity and never from the request.
   *
   * @return The name of the user.
   */
  private String whoIsLoggedIn() {

    return identity
        .getPrincipal()
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

    return new TaskState(task.isOpen())
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .completedAt(task.getCompletedAt())
        .completedBy(task.getCompletedBy());

  }

}
