package blueprint.workflowmodule.loanapproval;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
import io.vanillabp.cockpit.support.usertask.BcUserTaskExceptionHandler;

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
 * the same for every form of every module, so the Spring Boot support of the Business Cockpit
 * brings both exceptions and {@link BcUserTaskExceptionHandler}, which answers them. Nothing
 * registers the handler on its own, so the controller imports it.
 * </p>
 *
 * <p>
 * The paths and the records of the requests and answers come from {@link UserTaskFormsApi}, the
 * interface the build generates from {@code src/main/resources/openapi/user-task-forms.yaml}.
 * This class only implements it, and adds who may call each endpoint. The expressions of
 * {@code @PreAuthorize} name the parameters of the methods below, {@code #loanRequestId} and
 * {@code #taskId}.
 * </p>
 */
@RestController
@Import(BcUserTaskExceptionHandler.class)
public class UserTaskFormController implements UserTaskFormsApi {

  @Autowired
  private Service loanApproval;

  /**
   * What the form "Call customer" shows. In a real application the advisor calls from the CRM,
   * which the cockpit opens for this task, and the CRM calls these endpoints.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The customer, the attempts so far and whether the user may work on the task.
   */
  @Override
  @PreAuthorize("hasAuthority('"
      + Permission.CALL_CUSTOMER
      + "') or @userTaskAccess.mayRead(#loanRequestId, #taskId, authentication.name)")
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
      loanApproval.formOpenedForWork(loanRequestId, taskId, user.getName());
    }
    final CallCustomerTaskData data = task.getData();

    return new CallCustomerForm(
        loanRequest.getLoanRequestId(), loanRequest.getCustomer(), loanRequest.getPhone(), data.attempts(), data
            .callAgainAt(), data.note(), editable, stateOf(task));

  }

  /**
   * The advisor called and did not reach the customer. The task comes back the next morning.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   */
  @Override
  @Secured(Permission.CALL_CUSTOMER)
  public void customerNotReached(
      final String loanRequestId,
      final String taskId) {

    loanApproval.customerNotReached(loanRequestId, taskId, whoIsLoggedIn().getName());

  }

  /**
   * Completes the user task "Call customer".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the advisor noted.
   */
  @Override
  @Secured(Permission.CALL_CUSTOMER)
  public void completeCallCustomer(
      final String loanRequestId,
      final String taskId,
      final CallCustomerInput input) {

    loanApproval.customerCalled(loanRequestId, taskId, input.note(), whoIsLoggedIn().getName());

  }

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, the values of the form and whether the user may work on it.
   */
  @Override
  @PreAuthorize("hasAuthority('"
      + Permission.ASSESS_RISK
      + "') or @userTaskAccess.mayRead(#loanRequestId, #taskId, authentication.name)")
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
      loanApproval.formOpenedForWork(loanRequestId, taskId, user.getName());
    }
    final AssessRiskTaskData data = task.getData();

    return new AssessRiskForm(
        loanRequest.getLoanRequestId(), loanRequest.getCustomer(), loanRequest.getAmount(), loanRequest
            .getCreditRating(), data.riskAcceptable(), data.comment(), editable, stateOf(task));

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent.
   */
  @Override
  @Secured(Permission.ASSESS_RISK)
  public void completeAssessRisk(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    loanApproval.assessRisk(loanRequestId, taskId, input.riskAcceptable(), input.comment(), whoIsLoggedIn().getName());

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
  @PreAuthorize("hasAuthority('"
      + Permission.APPROVE_LOAN
      + "') or @userTaskAccess.mayRead(#loanRequestId, #taskId, authentication.name)")
  public ApproveLoanForm getApproveLoanForm(
      final String loanRequestId,
      final String taskId) {

    final var user = whoIsLoggedIn();
    final var loanRequest = loanApproval.withTask(loanRequestId, taskId, ApproveLoanTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final var editable = task.isOpen() && holds(user,
        Permission.APPROVE_LOAN) && !loanApproval.assessedTheRisk(loanRequest, user.getName());
    if (editable) {
      loanApproval.formOpenedForWork(loanRequestId, taskId, user.getName());
    }
    final ApproveLoanTaskData data = task.getData();

    return new ApproveLoanForm(
        loanRequest.getLoanRequestId(), loanRequest.getCustomer(), loanRequest.getAmount(), loanRequest
            .getRiskAcceptable(), loanRequest.getRiskComment(), loanRequest.getRiskAssessedBy(), data
                .approved(), data.comment(), editable, stateOf(task));

  }

  /**
   * Completes the user task "Approve loan".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user decided.
   */
  @Override
  @Secured(Permission.APPROVE_LOAN)
  public void completeApproveLoan(
      final String loanRequestId,
      final String taskId,
      final ApproveLoanInput input) {

    loanApproval.decide(loanRequestId, taskId, input.approved(), input.comment(), whoIsLoggedIn().getName());

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

  private static boolean holds(
      final Authentication user,
      final String authority) {

    return user
        .getAuthorities()
        .stream()
        .anyMatch(granted -> authority.equals(granted.getAuthority()));

  }

  /**
   * The user Spring Security logged in for this request. The generated interface knows nothing
   * about security, so the user is taken from the security context and never from the request.
   *
   * @return The user, with the authorities.
   */
  private static Authentication whoIsLoggedIn() {

    return SecurityContextHolder
        .getContext()
        .getAuthentication();

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

    return new TaskState(
        task.getCreatedAt(), task.getCompletedAt(), task.getCompletedBy(), List.copyOf(task.getEditors()), task
            .isOpen());

  }

}
