package blueprint.workflowmodule.loanapproval;

import java.util.List;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import blueprint.workflowmodule.loanapproval.api.UserTaskFormsApi;
import blueprint.workflowmodule.loanapproval.api.model.ApproveLoanForm;
import blueprint.workflowmodule.loanapproval.api.model.ApproveLoanInput;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.api.model.CallCustomerForm;
import blueprint.workflowmodule.loanapproval.api.model.CallCustomerInput;
import blueprint.workflowmodule.loanapproval.api.model.TaskState;
import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.usertask.ApproveLoanTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CallCustomerTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;

/**
 * The endpoints the forms of the user tasks call. GET reads what a form shows, and POST
 * completes the task with what the user entered.
 *
 * <p>
 * Reading a form lets in two kinds of user. One holds the authority of the task, the same
 * {@link Permission} the Business Cockpit got as the candidate group. The other worked on the
 * task before, and {@link UserTaskAccess} knows them. The second kind may have lost the
 * authority long ago, for example after moving to another team. The cockpit still shows them
 * the task, because the details provider reports them as admitted users.
 * </p>
 *
 * <p>
 * The controller knows which of the two let a user in, and that decides what the form offers.
 * A user who holds the authority gets the form to work on it ({@code editable}), and is written
 * into the task as an editor. A user who comes in as an earlier editor gets it to read. The
 * cockpit does not decide this. It lists the task and opens the form.
 * </p>
 *
 * <p>
 * Completing a task needs the authority, always. A POST is never let in by an earlier visit.
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
 * This class only implements it, and adds who may call each endpoint. A permission of
 * {@code @PermissionsAllowed} is checked by a method of {@link UserTaskAccess}, which gets the
 * parameters of the methods below by their names, {@code loanRequestId} and {@code taskId}.
 * </p>
 */
@ApplicationScoped
public class UserTaskFormController implements UserTaskFormsApi {

  @Inject
  Service loanApproval;

  /** The user Quarkus logged in for this request, with the roles. */
  @Inject
  SecurityIdentity identity;

  /**
   * What the form "Call customer" shows. In a real application the advisor calls from the CRM,
   * which the cockpit opens for this task, and the CRM calls these endpoints.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The customer, the attempts so far and whether the user may work on the task.
   */
  @Override
  @PermissionsAllowed(UserTaskAccess.READ_CALL_CUSTOMER)
  public CallCustomerForm getCallCustomerForm(
      final String loanRequestId,
      final String taskId) {

    final var user = whoIsLoggedIn();
    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, CallCustomerTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final var editable = task.isOpen() && holds(user, Permission.CALL_CUSTOMER);
    if (editable) {
      loanApproval.formOpenedForWork(loanRequestId, taskId, user.getPrincipal().getName());
    }
    final CallCustomerTaskData data = task.getData();

    return new CallCustomerForm()
        .loanRequestId(loanRequest.getLoanRequestId())
        .customer(loanRequest.getCustomer())
        .phone(loanRequest.getPhone())
        .attempts(data.attempts())
        .callAgainAt(data.callAgainAt())
        .note(data.note())
        .editable(editable)
        .task(stateOf(task));

  }

  /**
   * The advisor called and did not reach the customer. The task comes back the next morning.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   */
  @Override
  @RolesAllowed(Permission.CALL_CUSTOMER)
  public void customerNotReached(
      final String loanRequestId,
      final String taskId) {

    loanApproval.customerNotReached(loanRequestId, taskId, whoIsLoggedIn().getPrincipal().getName());

  }

  /**
   * Completes the user task "Call customer".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the advisor noted.
   */
  @Override
  @RolesAllowed(Permission.CALL_CUSTOMER)
  public void completeCallCustomer(
      final String loanRequestId,
      final String taskId,
      final CallCustomerInput input) {

    loanApproval.customerCalled(loanRequestId, taskId, input.getNote(), whoIsLoggedIn().getPrincipal().getName());

  }

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, the values of the form and whether the user may work on it.
   */
  @Override
  @PermissionsAllowed(UserTaskAccess.READ_ASSESS_RISK)
  public AssessRiskForm getAssessRiskForm(
      final String loanRequestId,
      final String taskId) {

    final var user = whoIsLoggedIn();
    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, AssessRiskTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final var editable = task.isOpen() && holds(user, Permission.ASSESS_RISK);
    if (editable) {
      loanApproval.formOpenedForWork(loanRequestId, taskId, user.getPrincipal().getName());
    }
    final AssessRiskTaskData data = task.getData();

    return new AssessRiskForm()
        .loanRequestId(loanRequest.getLoanRequestId())
        .customer(loanRequest.getCustomer())
        .amount(loanRequest.getAmount())
        .creditRating(loanRequest.getCreditRating())
        .riskAcceptable(data.riskAcceptable())
        .comment(data.comment())
        .editable(editable)
        .task(stateOf(task));

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent.
   */
  @Override
  @RolesAllowed(Permission.ASSESS_RISK)
  public void completeAssessRisk(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    loanApproval.assessRisk(loanRequestId, taskId, input.getRiskAcceptable(), input.getComment(),
        whoIsLoggedIn().getPrincipal().getName());

  }

  /**
   * What the form "Approve loan" shows. The user who assessed the risk may hold the authority
   * and still gets the form to read only, because of the four-eyes principle.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, the risk assessment, and whether the user may decide.
   */
  @Override
  @PermissionsAllowed(UserTaskAccess.READ_APPROVE_LOAN)
  public ApproveLoanForm getApproveLoanForm(
      final String loanRequestId,
      final String taskId) {

    final var user = whoIsLoggedIn();
    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, ApproveLoanTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final var editable = task.isOpen() && holds(user,
        Permission.APPROVE_LOAN) && !loanApproval.assessedTheRisk(loanRequest, user.getPrincipal().getName());
    if (editable) {
      loanApproval.formOpenedForWork(loanRequestId, taskId, user.getPrincipal().getName());
    }
    final ApproveLoanTaskData data = task.getData();

    return new ApproveLoanForm()
        .loanRequestId(loanRequest.getLoanRequestId())
        .customer(loanRequest.getCustomer())
        .amount(loanRequest.getAmount())
        .riskAcceptable(loanRequest.getRiskAcceptable())
        .riskComment(loanRequest.getRiskComment())
        .riskAssessedBy(loanRequest.getRiskAssessedBy())
        .approved(data.approved())
        .comment(data.comment())
        .editable(editable)
        .task(stateOf(task));

  }

  /**
   * Completes the user task "Approve loan".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user decided.
   */
  @Override
  @RolesAllowed(Permission.APPROVE_LOAN)
  public void completeApproveLoan(
      final String loanRequestId,
      final String taskId,
      final ApproveLoanInput input) {

    loanApproval.decide(loanRequestId, taskId, input.getApproved(), input.getComment(),
        whoIsLoggedIn().getPrincipal().getName());

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

  private static boolean holds(
      final SecurityIdentity user,
      final String authority) {

    return user.hasRole(authority);

  }

  /**
   * The user Quarkus logged in for this request. The generated interface knows nothing about
   * security, so the user is taken from the security identity and never from the request.
   *
   * @return The user, with the roles.
   */
  private SecurityIdentity whoIsLoggedIn() {

    return identity;

  }

  /**
   * The state of a user task, the same for every form.
   *
   * @param task The user task.
   * @return When it was opened and completed, by whom, who worked on it, and whether it is
   *         still open.
   */
  private static TaskState stateOf(
      final Task task) {

    return new TaskState(List.copyOf(task.getEditors()), task.isOpen())
        .createdAt(task.getCreatedAt())
        .completedAt(task.getCompletedAt())
        .completedBy(task.getCompletedBy());

  }

}
