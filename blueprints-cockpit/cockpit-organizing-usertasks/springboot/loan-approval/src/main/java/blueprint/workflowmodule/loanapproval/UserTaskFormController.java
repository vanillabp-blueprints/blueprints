package blueprint.workflowmodule.loanapproval;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.usertask.ApproveLoanTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CallCustomerTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;

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
   * What the form "Call customer" shows. In a real application the advisor calls from the CRM,
   * which the cockpit opens for this task, and the CRM calls these endpoints.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The user logged in.
   * @return The customer, the attempts so far and whether the user may work on the task.
   */
  @GetMapping("/call-customer/{taskId}")
  @PreAuthorize("hasAuthority('"
      + Permission.CALL_CUSTOMER
      + "') or @userTaskAccess.mayRead(#loanRequestId, #taskId, authentication.name)")
  public CallCustomerForm getCallCustomerForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      final Authentication user) {

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
            .callAgainAt(), data.note(), editable, TaskState.of(task));

  }

  /**
   * The advisor called and did not reach the customer. The task comes back the next morning.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The advisor logged in.
   */
  @PostMapping("/call-customer/{taskId}/not-reached")
  @Secured(Permission.CALL_CUSTOMER)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void customerNotReached(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      final Authentication user) {

    loanApproval.customerNotReached(loanRequestId, taskId, user.getName());

  }

  /**
   * Completes the user task "Call customer".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the advisor noted.
   * @param user          The advisor logged in.
   */
  @PostMapping("/call-customer/{taskId}")
  @Secured(Permission.CALL_CUSTOMER)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeCallCustomer(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final CallCustomerInput input,
      final Authentication user) {

    loanApproval.customerCalled(loanRequestId, taskId, input.note(), user.getName());

  }

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The user logged in.
   * @return The loan request, the values of the form and whether the user may work on it.
   */
  @GetMapping("/assess-risk/{taskId}")
  @PreAuthorize("hasAuthority('"
      + Permission.ASSESS_RISK
      + "') or @userTaskAccess.mayRead(#loanRequestId, #taskId, authentication.name)")
  public AssessRiskForm getAssessRiskForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      final Authentication user) {

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
            .getCreditRating(), data.riskAcceptable(), data.comment(), editable, TaskState.of(task));

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user sent.
   * @param user          The user logged in, who completes the task.
   */
  @PostMapping("/assess-risk/{taskId}")
  @Secured(Permission.ASSESS_RISK)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeAssessRisk(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final AssessRiskInput input,
      final Authentication user) {

    loanApproval.assessRisk(loanRequestId, taskId, input.riskAcceptable(), input.comment(), user.getName());

  }

  /**
   * What the form "Approve loan" shows. The user who assessed the risk may hold the authority
   * and still gets the form to read only, because of the four-eyes principle.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The user logged in.
   * @return The loan request, the risk assessment, and whether the user may decide.
   */
  @GetMapping("/approve-loan/{taskId}")
  @PreAuthorize("hasAuthority('"
      + Permission.APPROVE_LOAN
      + "') or @userTaskAccess.mayRead(#loanRequestId, #taskId, authentication.name)")
  public ApproveLoanForm getApproveLoanForm(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      final Authentication user) {

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
                .approved(), data.comment(), editable, TaskState.of(task));

  }

  /**
   * Completes the user task "Approve loan".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user decided.
   * @param user          The user logged in, who completes the task.
   */
  @PostMapping("/approve-loan/{taskId}")
  @Secured(Permission.APPROVE_LOAN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeApproveLoan(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestBody final ApproveLoanInput input,
      final Authentication user) {

    loanApproval.decide(loanRequestId, taskId, input.approved(), input.comment(), user.getName());

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
   * The state of a user task, the same for every form.
   *
   * @param createdAt   When the BPMS opened the task.
   * @param completedAt When the task was completed or canceled.
   * @param completedBy Who completed it, empty if the workflow canceled it.
   * @param editors     Who worked on it so far.
   * @param open        Whether somebody can still work on it.
   */
  public record TaskState(
                          OffsetDateTime createdAt,
                          OffsetDateTime completedAt,
                          String completedBy,
                          List<String> editors,
                          boolean open) {

    static TaskState of(
        final Task task) {

      return new TaskState(
          task.getCreatedAt(), task.getCompletedAt(), task.getCompletedBy(), List.copyOf(task.getEditors()), task
              .isOpen());

    }

  }

  /**
   * What the form "Call customer" shows.
   *
   * @param loanRequestId The id of the loan request.
   * @param customer      The name of the customer.
   * @param phone         The number to call.
   * @param attempts      How often the customer was not reached.
   * @param callAgainAt   When to call again, empty before the first failed call.
   * @param note          What the customer said, once the task is completed.
   * @param editable      Whether the user may work on the task, or only read it.
   * @param task          The state of the task.
   */
  public record CallCustomerForm(
                                 String loanRequestId,
                                 String customer,
                                 String phone,
                                 int attempts,
                                 OffsetDateTime callAgainAt,
                                 String note,
                                 boolean editable,
                                 TaskState task) {
  }

  /**
   * What the advisor noted in the form "Call customer".
   *
   * @param note What the customer said.
   */
  public record CallCustomerInput(
                                  String note) {
  }

  /**
   * What the form "Assess risk" shows.
   *
   * @param loanRequestId  The id of the loan request.
   * @param customer       The name of the customer.
   * @param amount         The amount requested.
   * @param creditRating   The rating of the service task before.
   * @param riskAcceptable The result, once the task is completed.
   * @param comment        The result, once the task is completed.
   * @param editable       Whether the user may work on the task, or only read it.
   * @param task           The state of the task.
   */
  public record AssessRiskForm(
                               String loanRequestId,
                               String customer,
                               Integer amount,
                               Integer creditRating,
                               Boolean riskAcceptable,
                               String comment,
                               boolean editable,
                               TaskState task) {
  }

  /**
   * What the user entered in the form "Assess risk".
   *
   * @param riskAcceptable Whether the risk is acceptable.
   * @param comment        Why.
   */
  public record AssessRiskInput(
                                Boolean riskAcceptable,
                                String comment) {
  }

  /**
   * What the form "Approve loan" shows.
   *
   * @param loanRequestId  The id of the loan request.
   * @param customer       The name of the customer.
   * @param amount         The amount requested.
   * @param riskAcceptable The result of the risk assessment.
   * @param riskComment    Why the risk was assessed as it was.
   * @param riskAssessedBy Who assessed the risk, and so must not decide.
   * @param approved       The decision, once the task is completed.
   * @param comment        Why, once the task is completed.
   * @param editable       Whether the user may decide, or only read.
   * @param task           The state of the task.
   */
  public record ApproveLoanForm(
                                String loanRequestId,
                                String customer,
                                Integer amount,
                                Boolean riskAcceptable,
                                String riskComment,
                                String riskAssessedBy,
                                Boolean approved,
                                String comment,
                                boolean editable,
                                TaskState task) {
  }

  /**
   * What the user decided in the form "Approve loan".
   *
   * @param approved Whether the loan is granted.
   * @param comment  Why.
   */
  public record ApproveLoanInput(
                                 Boolean approved,
                                 String comment) {
  }

}
