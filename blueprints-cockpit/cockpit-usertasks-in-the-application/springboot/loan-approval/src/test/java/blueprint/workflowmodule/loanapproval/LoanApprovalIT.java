package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CheckDocumentTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import blueprint.workflowmodule.loanapproval.model.usertask.TaskData;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS,
 * waits for the user tasks to be open and works on them the way the forms do.
 *
 * <p>
 * Each test asserts on the workflow aggregate and its tasks, never on the engine. The main
 * point is the second test: a saved draft stays in its task and does not reach the
 * aggregate.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  /** The documents of {@code loan-approval.yaml}, one user task "Check document" each. */
  private static final List<String> DOCUMENTS = List.of("id-card", "income-statement");

  @Autowired
  private Service loanApproval;

  @Autowired
  private AggregateRepository loanApprovals;

  @Test
  public void everyOpenUserTaskIsARowOfItsOwn() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();

    final var loanRequest = loanApprovals
        .findById(loanRequestId)
        .orElseThrow();

    // One risk assessment and one document check per document, all open at the same time.
    assertThat(openTasks(loanRequest, AssessRiskTaskData.class)).hasSize(1);
    assertThat(openTasks(loanRequest, CheckDocumentTaskData.class))
        .extracting(task -> task
            .<CheckDocumentTaskData>getData()
            .documentId())
        .containsExactlyInAnyOrderElementsOf(DOCUMENTS);
    assertThat(loanRequest.getTasks().values())
        .allSatisfy(task -> assertThat(task.getCreatedAt()).isNotNull());
    assertThat(loanRequest.getCreditRating()).isEqualTo(50);

  }

  @Test
  public void aDraftStaysInItsTaskAndNeverReachesTheAggregate() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();
    final var riskAssessment = taskId(loanRequestId, AssessRiskTaskData.class);

    loanApproval.saveRiskAssessmentDraft(loanRequestId, riskAssessment, false, "first impression");

    final var loanRequest = loanApprovals
        .findById(loanRequestId)
        .orElseThrow();
    final var task = loanRequest
        .getTask(riskAssessment)
        .orElseThrow();

    // The task holds the draft ...
    assertThat(task.<AssessRiskTaskData>getData())
        .isEqualTo(new AssessRiskTaskData(false, "first impression"));
    assertThat(task.getUpdatedAt()).isNotNull();
    assertThat(task.isOpen()).isTrue();
    // ... and the aggregate does not know about it. A branch which runs at the same time,
    // like the document checks, sees that nobody has assessed the risk yet.
    assertThat(loanRequest.getRiskAcceptable()).isNull();
    assertThat(loanRequest.getRiskComment()).isNull();
    assertThat(loanRequest.getLoanApproved()).isNull();

  }

  @Test
  public void completingATaskMovesItsResultIntoTheAggregate() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();
    final var riskAssessment = taskId(loanRequestId, AssessRiskTaskData.class);

    loanApproval.saveRiskAssessmentDraft(loanRequestId, riskAssessment, false, "first impression");
    loanApproval.assessRisk(loanRequestId, riskAssessment, true, "stable income", "risk-officer");

    for (final var documentCheck : taskIds(loanRequestId, CheckDocumentTaskData.class)) {
      loanApproval.saveDocumentCheckDraft(loanRequestId, documentCheck, null, "looks fine");
      loanApproval.checkDocument(loanRequestId, documentCheck, true, "in order", "clerk");
    }

    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getLoanApproved()));

    // The results are in the aggregate, the last drafts are not.
    assertThat(loanRequest.getRiskAcceptable()).isTrue();
    assertThat(loanRequest.getRiskComment()).isEqualTo("stable income");
    assertThat(loanRequest.getDocumentChecks())
        .containsOnlyKeys(DOCUMENTS)
        .doesNotContainValue(false);
    // Every task stays, with who completed it.
    assertThat(loanRequest.getTask(riskAssessment))
        .get()
        .satisfies(task -> {
          assertThat(task.isOpen()).isFalse();
          assertThat(task.getCompletedBy()).isEqualTo("risk-officer");
          assertThat(task.<AssessRiskTaskData>getData())
              .isEqualTo(new AssessRiskTaskData(true, "stable income"));
        });
    assertThat(openTasks(loanRequest, CheckDocumentTaskData.class)).isEmpty();
    assertThat(loanRequest.getTasks().values())
        .filteredOn(task -> task.holds(CheckDocumentTaskData.class))
        .allSatisfy(task -> assertThat(task.getCompletedBy()).isEqualTo("clerk"));

  }

  @Test
  public void tasksTheWorkflowTakesAwayAreMarkedAsCanceled() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();
    final var documentCheck = taskId(loanRequestId, CheckDocumentTaskData.class);

    loanApproval.saveDocumentCheckDraft(loanRequestId, documentCheck, false, "blurred copy");
    loanApproval.assessRisk(
        loanRequestId,
        taskId(loanRequestId, AssessRiskTaskData.class),
        false,
        "income too low",
        "risk-officer");

    // A too high risk ends the workflow at a terminate end event, which cancels the open
    // document checks. Each handler is called with CANCELED and marks its task.
    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.FALSE.equals(aggregate.getLoanApproved()) && aggregate
            .getTasks()
            .values()
            .stream()
            .filter(task -> task.holds(CheckDocumentTaskData.class))
            .allMatch(Task::isCanceled));

    // The draft of a canceled task stays in the task and never reaches the aggregate.
    assertThat(loanRequest.getDocumentChecks()).isEmpty();
    assertThat(loanRequest
        .getTask(documentCheck)
        .orElseThrow()
        .<CheckDocumentTaskData>getData()
        .remark()).isEqualTo("blurred copy");
    assertThatThrownBy(() -> loanApproval.checkDocument(loanRequestId, documentCheck, true, "", "clerk"))
        .isInstanceOf(Service.TaskNotOpen.class);

  }

  @Test
  public void aCompletedTaskCannotBeCompletedAgain() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();
    final var documentCheck = taskId(loanRequestId, CheckDocumentTaskData.class);

    loanApproval.checkDocument(loanRequestId, documentCheck, true, "in order", "clerk");

    // The same form sent a second time, or a draft saved after completing.
    assertThatThrownBy(() -> loanApproval.checkDocument(loanRequestId, documentCheck, false, "", "clerk"))
        .isInstanceOf(Service.TaskNotOpen.class);
    assertThatThrownBy(() -> loanApproval.saveDocumentCheckDraft(loanRequestId, documentCheck, false, ""))
        .isInstanceOf(Service.TaskNotOpen.class);
    // A task id of another kind is refused, even when it is open.
    assertThatThrownBy(() -> loanApproval.checkDocument(
        loanRequestId, taskId(loanRequestId, AssessRiskTaskData.class), true, "", "clerk"))
        .isInstanceOf(Service.UnknownTask.class);

    assertThat(loanApprovals
        .findById(loanRequestId)
        .orElseThrow()
        .getDocumentChecks()).hasSize(1);

  }

  private String requestLoanAndWaitForTheTasks() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getTasks().size() == 1 + DOCUMENTS.size());

    return loanRequestId;

  }

  private String taskId(
      final String loanRequestId,
      final Class<? extends TaskData> kind) {

    return taskIds(loanRequestId, kind)
        .getFirst();

  }

  private List<String> taskIds(
      final String loanRequestId,
      final Class<? extends TaskData> kind) {

    return openTasks(loanApprovals
        .findById(loanRequestId)
        .orElseThrow(), kind)
        .stream()
        .map(Task::getTaskId)
        .toList();

  }

  private static List<Task> openTasks(
      final Aggregate loanRequest,
      final Class<? extends TaskData> kind) {

    return loanRequest
        .getTasks()
        .values()
        .stream()
        .filter(Task::isOpen)
        .filter(task -> task.holds(kind))
        .toList();

  }

}
