package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.api.model.ApproveLoanInput;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.api.model.CallCustomerInput;
import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.usertask.ApproveLoanTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CallCustomerTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.TaskData;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialWorkflowlistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.EndReason;
import io.vanillabp.cockpit.support.usertask.BcTaskNotOpenException;
import io.vanillabp.cockpit.support.usertask.BcUserTaskExceptionHandler;
import jakarta.persistence.EntityManagerFactory;

/**
 * Plays every pattern of the blueprint through, each in a test of its own, and checks what the
 * workflow module reported to the Business Cockpit.
 *
 * <p>
 * The test calls the controllers as beans, as the user who would be logged in. So the method
 * security of the module is checked, without the login of an application in front of it. The
 * reports go to the dev shell simulator, and the test reads them back from there, see
 * {@link CockpitSimulator}.
 * </p>
 */
@Import(StoredLoanApprovals.class)
public class OrganizingUserTasksIT extends WorkflowModuleTest {

  private static final String CUSTOMER = "Jane Roe";

  private static final String PHONE = "+43 1 2345 678";

  private static final int AMOUNT = 5000;

  @Autowired
  private ApiController api;

  @Autowired
  private UserTaskFormController forms;

  @Autowired
  private StoredLoanApprovals stored;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private BcUserTaskExceptionHandler userTaskExceptionHandler;

  /** The task list of the cockpit, read from the simulator. */
  @Autowired
  private OfficialTasklistApi tasklist;

  /** The list of workflows of the cockpit, read from the simulator. */
  @Autowired
  private OfficialWorkflowlistApi workflowlist;

  @DynamicPropertySource
  static void reportToTheSimulator(
      final DynamicPropertyRegistry properties) {

    properties.add("vanillabp.cockpit.rest.base-url", () -> CockpitSimulator.start().baseUrl());
    properties.add("spring.http.serviceclient.official-gui-api.base-url",
        () -> CockpitSimulator.start().cockpitBaseUrl());

  }

  @AfterEach
  public void logOut() {

    SecurityContextHolder.clearContext();

  }

  /**
   * Who caused a report is the initiator, and the module knows it from the aggregate. The
   * advisor who requested the loan caused the first task. The cockpit does not notify the
   * advisor about it.
   */
  @Test
  public void theInitiatorIsWhoCausedTheReport() {

    final var loanRequestId = requestLoanAs("alice");
    final var call = openTask(loanRequestId, CallCustomerTaskData.class);

    assertThat(cockpit().awaitUserTaskReport(call, report -> true))
        .containsEntry("initiator", "alice");
    final var workflow = cockpit().awaitWorkflow(workflowlist, loanRequestId, found -> true);
    assertThat(workflow.getInitiator().getId()).isEqualTo("alice");
    assertThat(workflow.getTitle())
        .isEqualTo(Map.of(
            "en", "Loan request of Jane Roe over 5000 EUR",
            "de", "Kreditantrag von Jane Roe über 5000 EUR"));

    callTheCustomer(loanRequestId, call, "alice");
    final var riskAssessment = openTask(loanRequestId, AssessRiskTaskData.class);

    // The risk assessment was opened because alice completed her call.
    assertThat(cockpit().awaitUserTaskReport(riskAssessment, report -> true))
        .containsEntry("initiator", "alice");

  }

  /**
   * "Call customer" belongs to the advisor of the customer, so it has an assignee. It opens in
   * the CRM and not in a form of the module.
   */
  @Test
  public void theCallIsAssignedToTheAdvisorAndOpensInTheCrm() {

    final var loanRequestId = requestLoanAs("alice");
    final var call = openTask(loanRequestId, CallCustomerTaskData.class);

    final var report = cockpit().awaitUserTaskReport(call, found -> true);

    assertThat(report)
        .containsEntry("assignee", "alice")
        .containsEntry("candidateGroups", List.of(Permission.CALL_CUSTOMER))
        .containsEntry("notificationDelivery", "USER_CONFIG")
        .containsEntry("uiUriPath", "https://crm.example.com/loan-requests/"
            + loanRequestId
            + "/call")
        .containsEntry("title", Map.of(
            "en", "Call Jane Roe about 5000 EUR",
            "de", "Jane Roe wegen 5000 EUR anrufen"));
    assertThat(dateOf(report, "dueDate"))
        .isCloseTo(aggregate(loanRequestId).getRequestedAt().plusDays(1), within(1, ChronoUnit.SECONDS));

  }

  /**
   * The advisor did not reach the customer. The task gets a follow-up date on the next morning,
   * and a title which says that this is another attempt.
   */
  @Test
  public void aCustomerNotReachedComesBackTheNextMorning() {

    final var loanRequestId = requestLoanAs("alice");
    final var call = openTask(loanRequestId, CallCustomerTaskData.class);

    logIn("alice", Permission.CALL_CUSTOMER);
    forms.customerNotReached(loanRequestId, call);

    final var report = cockpit().awaitUserTaskReport(call, found -> found.get("followUpDate") != null);

    final var vienna = ZoneId.of("Europe/Vienna");
    final var tomorrowAtNine = OffsetDateTime
        .now(vienna)
        .toLocalDate()
        .plusDays(1)
        .atTime(9, 0)
        .atZone(vienna)
        .toOffsetDateTime();
    assertThat(dateOf(report, "followUpDate").toInstant()).isEqualTo(tomorrowAtNine.toInstant());
    assertThat(report).containsEntry("title", Map.of(
        "en", "Call Jane Roe again, attempt 2",
        "de", "Jane Roe erneut anrufen, Versuch 2"));
    // The task is still open. Only the list of open tasks leaves it out until tomorrow.
    assertThat(aggregate(loanRequestId).getTask(call).orElseThrow().isOpen()).isTrue();

  }

  /**
   * The customer gets another advisor. The cockpit keeps the assignee it got first, so the
   * workflow ends the task and enters it again. The new task belongs to the new advisor, and
   * the team lead who made the change caused the report.
   */
  @Test
  public void anotherAdvisorGetsTheCallAsANewTask() {

    final var loanRequestId = requestLoanAs("alice");
    final var firstCall = openTask(loanRequestId, CallCustomerTaskData.class);

    logIn("lea", Permission.CHANGE_ADVISOR);
    api.changeAdvisor(loanRequestId, "erin");

    final var loanRequest = awaitAggregate(
        stored::find,
        loanRequestId,
        found -> found.getTask(firstCall).orElseThrow()
            .isCanceled() && openTaskId(found, CallCustomerTaskData.class) != null);
    final var secondCall = openTaskId(loanRequest, CallCustomerTaskData.class);

    assertThat(secondCall).isNotEqualTo(firstCall);
    cockpit().awaitUserTask(tasklist, firstCall, userTask -> userTask.getEndReason() == EndReason.CANCELLED);
    assertThat(cockpit().awaitUserTaskReport(secondCall, report -> true))
        .containsEntry("assignee", "erin")
        .containsEntry("initiator", "lea");

  }

  /**
   * "Assess risk" is for the risk team: a candidate group and no assignee. The cockpit
   * notifies nobody, because the module writes to the team's mailbox itself. Its title comes
   * from a template per language, and its search text has the phone number, which no column
   * shows.
   */
  @Test
  public void theRiskAssessmentIsOfferedToTheTeamAndNotifiesNobody() {

    final var loanRequestId = requestLoanAs("alice");
    callTheCustomer(loanRequestId, openTask(loanRequestId, CallCustomerTaskData.class), "alice");
    final var riskAssessment = openTask(loanRequestId, AssessRiskTaskData.class);

    final var report = cockpit().awaitUserTaskReport(riskAssessment, found -> true);

    assertThat(report.get("assignee")).isNull();
    assertThat(report)
        .containsEntry("candidateGroups", List.of(Permission.ASSESS_RISK))
        .containsEntry("notificationDelivery", "SUPPRESS")
        .containsEntry("title", Map.of(
            "en", "Assess the risk of 5000 EUR for Jane Roe",
            "de", "Risiko von 5000 EUR für Jane Roe bewerten"));
    assertThat(report.get("details"))
        .isEqualTo(Map.of("customer", CUSTOMER, "amount", AMOUNT, "creditRating", 50, "advisor", "alice"));
    assertThat((String) report.get("detailsFulltextSearch")).contains(PHONE, CUSTOMER);
    assertThat(dateOf(report, "dueDate"))
        .isCloseTo(aggregate(loanRequestId).getRequestedAt().plusDays(2), within(1, ChronoUnit.SECONDS));

  }

  /**
   * Who assessed the risk must not approve the loan. The cockpit gets that user as an excluded
   * candidate. The module refuses the approval from that user as well, because the cockpit is
   * not the only way to call an endpoint. The approval is due when the bank promised an answer,
   * so the cockpit notifies whatever the approvers configured.
   */
  @Test
  public void whoAssessedTheRiskDoesNotApproveTheLoan() {

    final var loanRequestId = requestLoanAs("alice");
    callTheCustomer(loanRequestId, openTask(loanRequestId, CallCustomerTaskData.class), "alice");
    final var riskAssessment = openTask(loanRequestId, AssessRiskTaskData.class);
    logIn("carol", Permission.ASSESS_RISK, Permission.APPROVE_LOAN);
    forms.completeAssessRisk(loanRequestId, riskAssessment, new AssessRiskInput(true, "stable income"));
    final var approval = openTask(loanRequestId, ApproveLoanTaskData.class);

    final var report = cockpit().awaitUserTaskReport(approval, found -> true);
    assertThat(report)
        .containsEntry("initiator", "carol")
        .containsEntry("candidateGroups", List.of(Permission.APPROVE_LOAN))
        .containsEntry("excludedCandidateUsers", List.of("carol"))
        .containsEntry("notificationDelivery", "FORCE")
        .containsEntry("title", Map.of(
            "en", "Approve 5000 EUR for Jane Roe",
            "de", "5000 EUR für Jane Roe genehmigen"));
    assertThat(dateOf(report, "dueDate"))
        .isCloseTo(aggregate(loanRequestId).getAnswerPromisedBy(), within(1, ChronoUnit.SECONDS));

    // carol holds the authority, so she may read the form, but not decide.
    logIn("carol", Permission.ASSESS_RISK, Permission.APPROVE_LOAN);
    assertThat(forms.getApproveLoanForm(loanRequestId, approval).editable()).isFalse();
    assertThatThrownBy(() -> forms.completeApproveLoan(
        loanRequestId, approval, new ApproveLoanInput(true, "fine")))
        .isInstanceOf(Service.FourEyesViolation.class);

    logIn("dave", Permission.APPROVE_LOAN);
    assertThat(forms.getApproveLoanForm(loanRequestId, approval).editable()).isTrue();
    forms.completeApproveLoan(loanRequestId, approval, new ApproveLoanInput(true, "fine"));

    assertThat(awaitAggregate(stored::find, loanRequestId, found -> found.getLoanApproved() != null))
        .extracting(Aggregate::getLoanApproved, Aggregate::getDecidedBy)
        .containsExactly(true, "dave");

    // The same form sent a second time. The module imports the handler of the cockpit, which
    // answers that the task is done with 404.
    final var refused = catchThrowableOfType(
        BcTaskNotOpenException.class,
        () -> forms.completeApproveLoan(loanRequestId, approval, new ApproveLoanInput(true, "fine")));
    assertThat(userTaskExceptionHandler
        .taskNotOpen(refused)
        .getStatusCode().value()).isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);

  }

  /**
   * A user who opened a form to work on it is written into the task. The cockpit gets the user
   * as an admitted user, and the form lets the user in to read, even after the user lost the
   * authority. Somebody who never worked on the task is refused.
   */
  @Test
  public void whoWorkedOnATaskMayStillReadIt() {

    final var loanRequestId = requestLoanAs("alice");
    callTheCustomer(loanRequestId, openTask(loanRequestId, CallCustomerTaskData.class), "alice");
    final var riskAssessment = openTask(loanRequestId, AssessRiskTaskData.class);

    // bob is in the risk team and opens the form to work on it.
    logIn("bob", Permission.ASSESS_RISK);
    assertThat(forms.getAssessRiskForm(loanRequestId, riskAssessment).editable()).isTrue();
    cockpit().awaitUserTaskReport(
        riskAssessment, report -> List.of("bob").equals(report.get("admittedUsers")));

    // bob moved to another team and has no authority of this module any more.
    logIn("bob");
    final var form = forms.getAssessRiskForm(loanRequestId, riskAssessment);
    assertThat(form.editable()).isFalse();
    assertThat(form.customer()).isEqualTo(CUSTOMER);
    assertThatThrownBy(() -> forms.completeAssessRisk(
        loanRequestId, riskAssessment, new AssessRiskInput(true, "")))
        .isInstanceOf(AccessDeniedException.class);

    // mallory never worked on it.
    logIn("mallory");
    assertThatThrownBy(() -> forms.getAssessRiskForm(loanRequestId, riskAssessment))
        .isInstanceOf(AccessDeniedException.class);

  }

  /**
   * The tasks of a loan approval and the data of each task are loaded only when they are
   * read, as in the base blueprint.
   */
  @Test
  public void theTasksAndTheirDataAreLoadedOnlyWhenRead() {

    final var loanRequestId = requestLoanAs("alice");
    final var call = openTask(loanRequestId, CallCustomerTaskData.class);
    final var persistence = entityManagerFactory.getPersistenceUnitUtil();

    new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {

      final var loanRequest = loanApprovals
          .findById(loanRequestId)
          .orElseThrow();
      assertThat(persistence.isLoaded(loanRequest, "tasks")).isFalse();

      final var task = loanRequest
          .getTask(call)
          .orElseThrow();
      assertThat(persistence.isLoaded(loanRequest, "tasks")).isTrue();
      assertThat(persistence.isLoaded(task, "data")).isFalse();

      assertThat(task.<CallCustomerTaskData>getData()).isEqualTo(CallCustomerTaskData.empty());
      assertThat(persistence.isLoaded(task, "data")).isTrue();

    });

  }

  private CockpitSimulator cockpit() {

    return CockpitSimulator.start();

  }

  private String requestLoanAs(
      final String advisor) {

    logIn(advisor, Permission.REQUEST_LOAN, Permission.CALL_CUSTOMER);
    final var loanRequestId = api.start(AMOUNT, CUSTOMER, PHONE);
    SecurityContextHolder.clearContext();
    return loanRequestId;

  }

  private void callTheCustomer(
      final String loanRequestId,
      final String call,
      final String advisor) {

    logIn(advisor, Permission.CALL_CUSTOMER);
    forms.getCallCustomerForm(loanRequestId, call);
    forms.completeCallCustomer(loanRequestId, call, new CallCustomerInput("wants it soon"));
    SecurityContextHolder.clearContext();

  }

  /** Waits for an open task of the given kind and returns its id. */
  private String openTask(
      final String loanRequestId,
      final Class<? extends TaskData> kind) {

    return openTaskId(awaitAggregate(
        stored::find,
        loanRequestId,
        found -> openTaskId(found, kind) != null), kind);

  }

  private static String openTaskId(
      final Aggregate loanRequest,
      final Class<? extends TaskData> kind) {

    return loanRequest
        .getTasks()
        .values()
        .stream()
        .filter(task -> task.isOpen() && task.holds(kind))
        .map(task -> task.getTaskId())
        .findFirst()
        .orElse(null);

  }

  private Aggregate aggregate(
      final String loanRequestId) {

    return stored
        .find(loanRequestId)
        .orElseThrow();

  }

  /** Logs the user in for the calls which follow, the way the login of an application would. */
  private static void logIn(
      final String user,
      final String... authorities) {

    final var authentication = UsernamePasswordAuthenticationToken.authenticated(
        user, "demo", AuthorityUtils.createAuthorityList(authorities));
    SecurityContextHolder
        .getContext()
        .setAuthentication(authentication);

  }

  private static OffsetDateTime dateOf(
      final Map<String, Object> report,
      final String key) {

    return OffsetDateTime.parse((String) report.get(key));

  }

}
