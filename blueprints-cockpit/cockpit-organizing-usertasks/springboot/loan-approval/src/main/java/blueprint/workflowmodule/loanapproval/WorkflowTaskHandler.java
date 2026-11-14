package blueprint.workflowmodule.loanapproval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.usertask.CallCustomerTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import io.vanillabp.spi.cockpit.usertask.NotificationDelivery;
import io.vanillabp.spi.cockpit.usertask.PrefilledUserTaskDetails;
import io.vanillabp.spi.cockpit.usertask.UserTaskDetails;
import io.vanillabp.spi.cockpit.usertask.UserTaskDetailsProvider;
import io.vanillabp.spi.cockpit.workflow.PrefilledWorkflowDetails;
import io.vanillabp.spi.cockpit.workflow.WorkflowDetails;
import io.vanillabp.spi.cockpit.workflow.WorkflowDetailsProvider;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application, and what the Business Cockpit shows about it.
 *
 * <p>
 * The {@code @WorkflowTask} methods are the incoming half of the BPMN wiring. On
 * {@code CREATED} the business code adds a row for the user task to the aggregate. On
 * {@code CANCELED} it marks that row as canceled.
 * </p>
 *
 * <p>
 * The {@code @UserTaskDetailsProvider} methods say how each user task is organized: who caused
 * the report, who gets the task and who must not get it, who hears about it, by when it is
 * due, when it comes back, who may still read it, and how it is titled. The cockpit adapter
 * calls them for every report of a task. Everything they set is read from the aggregate, so a
 * report built later, or built again, says the same.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP loads
 * the aggregate, runs the method and saves the aggregate in one transaction it owns. A details
 * provider must not change the aggregate, because nobody saves it after the provider ran.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 * @see <a href=
 *      "https://github.com/vanillabp/business-cockpit/wiki/Reporting-workflows-and-user-tasks#organizing-user-tasks">Organizing
 *      user tasks</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  /**
   * The template which titles the user task "Call customer" once the customer was not reached.
   * It is in the directory of that task, next to the default template {@code task-title.ftl}.
   */
  private static final String CALL_AGAIN_TEMPLATE = "call-again.ftl";

  @Autowired
  private Service loanApproval;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanRequest) {

    loanApproval.assessCreditRating(loanRequest);

  }

  /**
   * Called by VanillaBP when the user task "Call customer" is created, and again if the
   * workflow cancels it because the customer got another advisor.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id the BPMS gave this user task.
   * @param event       Whether the task was created or canceled.
   */
  @WorkflowTask
  public void callCustomer(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.callCustomerOpened(loanRequest, taskId);
      case CANCELED -> loanApproval.taskCanceled(loanRequest, taskId);
      default -> throw unexpected(event);
    }

  }

  /**
   * Called by VanillaBP when the user task "Assess risk" is created.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id the BPMS gave this user task.
   * @param event       Whether the task was created or canceled.
   */
  @WorkflowTask
  public void assessRisk(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.riskAssessmentOpened(loanRequest, taskId);
      case CANCELED -> loanApproval.taskCanceled(loanRequest, taskId);
      default -> throw unexpected(event);
    }

  }

  /**
   * Called by VanillaBP when the user task "Approve loan" is created.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id the BPMS gave this user task.
   * @param event       Whether the task was created or canceled.
   */
  @WorkflowTask
  public void approveLoan(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.approvalOpened(loanRequest, taskId);
      case CANCELED -> loanApproval.taskCanceled(loanRequest, taskId);
      default -> throw unexpected(event);
    }

  }

  /**
   * "Call customer" belongs to one person: the advisor of the customer. The business knows who
   * that is, so the task gets an assignee. The candidate group lets a colleague take it over in
   * the cockpit, for example while the advisor is ill.
   *
   * <p>
   * The advisor calls from the CRM, so the cockpit opens the CRM for this task and not a form
   * of the module. A path which starts with {@code https://} is opened as it is.
   * </p>
   *
   * <p>
   * When the customer was not reached, the task gets a follow-up date. It leaves the list of
   * open tasks and comes back the next morning. Its title then says that this is another
   * attempt. The code names the template for that title, {@code call-again.ftl}, for every
   * language.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the BPMS reported, to be completed here.
   * @return The details to report.
   */
  @UserTaskDetailsProvider(taskDefinition = "callCustomer")
  public UserTaskDetails callCustomerDetails(
      final Aggregate loanRequest,
      final PrefilledUserTaskDetails details) {

    reportWhatEveryTaskReports(loanRequest, details);

    details.setAssignee(loanRequest.getAdvisor());
    details.setCandidateGroups(List.of(Permission.CALL_CUSTOMER));
    details.setDueDate(loanRequest
        .getRequestedAt()
        .plus(properties.getCallCustomerWithin()));
    details.setNotificationDelivery(NotificationDelivery.USER_CONFIG);
    details.setUiUriPath(properties.getCrmAddress()
        + "/loan-requests/"
        + loanRequest.getLoanRequestId()
        + "/call");

    loanRequest
        .getTask(details.getId())
        .map(Task::<CallCustomerTaskData>getData)
        .filter(call -> call.callAgainAt() != null)
        .ifPresent(call -> {
          details.setFollowUpDate(call.callAgainAt());
          details.setTitle(eachLanguage(CALL_AGAIN_TEMPLATE));
        });

    return details;

  }

  /**
   * "Assess risk" is for any member of the risk team, so it has a candidate group and no
   * assignee. The first one who takes it in the cockpit becomes the assignee.
   *
   * <p>
   * The cockpit notifies nobody about it. The team works from a shared mailbox, and the module
   * writes there itself. A notification on top would tell everybody twice.
   * </p>
   *
   * <p>
   * The title comes from the template {@code task-title.ftl} in the directory of this task,
   * one file per language. No title is set here, so the cockpit adapter renders the default
   * template.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the BPMS reported, to be completed here.
   * @return The details to report.
   */
  @UserTaskDetailsProvider(taskDefinition = "assessRisk")
  public UserTaskDetails assessRiskDetails(
      final Aggregate loanRequest,
      final PrefilledUserTaskDetails details) {

    reportWhatEveryTaskReports(loanRequest, details);

    details.setCandidateGroups(List.of(Permission.ASSESS_RISK));
    details.setDueDate(loanRequest
        .getRequestedAt()
        .plus(properties.getAssessRiskWithin()));
    details.setNotificationDelivery(NotificationDelivery.SUPPRESS);

    return details;

  }

  /**
   * "Approve loan" is for the approvers, but not for the user who assessed the risk. That user
   * is an excluded candidate, which is the four-eyes principle. The cockpit does not offer the
   * task to the user, even if the user holds the authority. The module refuses the approval
   * from that user as well, because the cockpit is not the only way to call an endpoint.
   *
   * <p>
   * It is due on the day the bank promised the customer an answer. Missing that costs the bank
   * the customer, so the cockpit notifies the approvers whatever they configured.
   * </p>
   *
   * <p>
   * The title is set here as text, one per language, without a template. That is the simple
   * way, and it is enough while a title is a single line.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the BPMS reported, to be completed here.
   * @return The details to report.
   */
  @UserTaskDetailsProvider(taskDefinition = "approveLoan")
  public UserTaskDetails approveLoanDetails(
      final Aggregate loanRequest,
      final PrefilledUserTaskDetails details) {

    reportWhatEveryTaskReports(loanRequest, details);

    details.setCandidateGroups(List.of(Permission.APPROVE_LOAN));
    if (loanRequest.getRiskAssessedBy() != null) {
      details.setExcludedCandidateUsers(List.of(loanRequest.getRiskAssessedBy()));
    }
    details.setDueDate(loanRequest.getAnswerPromisedBy());
    details.setNotificationDelivery(NotificationDelivery.FORCE);
    details.setTitle(Map.of(
        "en", "Approve "
            + loanRequest.getAmount()
            + " EUR for "
            + loanRequest.getCustomer(),
        "de", loanRequest.getAmount()
            + " EUR für "
            + loanRequest.getCustomer()
            + " genehmigen"));

    return details;

  }

  /**
   * What the cockpit shows about the whole loan approval. The initiator is mandatory here as
   * well. Every user who works on one of its user tasks may open the case.
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the BPMS reported, to be completed here.
   * @return The details to report.
   */
  @WorkflowDetailsProvider
  public WorkflowDetails loanApprovalDetails(
      final Aggregate loanRequest,
      final PrefilledWorkflowDetails details) {

    details.setInitiator(loanRequest.getLastActionBy());
    details.setAccessibleToGroups(List.of(
        Permission.REQUEST_LOAN, Permission.CALL_CUSTOMER, Permission.ASSESS_RISK, Permission.APPROVE_LOAN));
    details.setDetails(columns(loanRequest));
    details.setTemplateContext(TitleContext.of(loanRequest, null));

    return details;

  }

  /**
   * What every user task of this workflow reports the same way.
   *
   * <ul>
   * <li>The initiator is the user who caused the latest change, read from the aggregate. The
   * cockpit does not notify that user about the report. So nobody gets a notification about a
   * task their own click opened.</li>
   * <li>The details are the business data the columns of the task list show.</li>
   * <li>The template context is what the templates of the titles and of the search text
   * read.</li>
   * <li>The admitted users are everybody who worked on the task. They may still find and read
   * it after they lost the authority for it.</li>
   * </ul>
   */
  private void reportWhatEveryTaskReports(
      final Aggregate loanRequest,
      final PrefilledUserTaskDetails details) {

    final var task = loanRequest.getTask(details.getId());

    details.setInitiator(loanRequest.getLastActionBy());
    details.setDetails(columns(loanRequest));
    details.setTemplateContext(TitleContext.of(loanRequest, task
        .map(Task::getData)
        .filter(CallCustomerTaskData.class::isInstance)
        .map(CallCustomerTaskData.class::cast)
        .orElse(null)));
    details.setAdmittedUsers(task
        .map(Task::getEditors)
        .map(List::copyOf)
        .orElse(List.of()));

  }

  /**
   * The business data of a loan approval which the lists of the cockpit show as columns. The
   * phone number is missing on purpose: no list shows it, but the search text has it.
   */
  private static Map<String, Object> columns(
      final Aggregate loanRequest) {

    final var columns = new LinkedHashMap<String, Object>();
    columns.put("customer", loanRequest.getCustomer());
    columns.put("amount", loanRequest.getAmount());
    columns.put("creditRating", loanRequest.getCreditRating());
    columns.put("advisor", loanRequest.getAdvisor());
    return columns;

  }

  /** The same template name for every language the module reports in. */
  private static Map<String, String> eachLanguage(
      final String templateName) {

    return Map.of("en", templateName, "de", templateName);

  }

  private static IllegalStateException unexpected(
      final TaskEvent.Event event) {

    return new IllegalStateException("Unexpected task event '"
        + event
        + "'");

  }

  /**
   * What the templates of the titles and of the search text read. A small record and not the
   * aggregate itself, so a template sees only what it needs, and the names it uses do not
   * change when the aggregate does.
   *
   * @param loanRequestId The id of the loan request.
   * @param customer      The name of the customer.
   * @param phone         The phone number of the customer.
   * @param amount        The amount requested.
   * @param advisor       The advisor of the customer.
   * @param attempts      How often the customer was not reached, 0 outside "Call customer".
   */
  public record TitleContext(
                             String loanRequestId,
                             String customer,
                             String phone,
                             int amount,
                             String advisor,
                             int attempts) {

    static TitleContext of(
        final Aggregate loanRequest,
        final CallCustomerTaskData call) {

      return new TitleContext(
          loanRequest.getLoanRequestId(), loanRequest.getCustomer(), loanRequest.getPhone(), loanRequest
              .getAmount(), loanRequest.getAdvisor(), call == null ? 0 : call.attempts());

    }

  }

}
