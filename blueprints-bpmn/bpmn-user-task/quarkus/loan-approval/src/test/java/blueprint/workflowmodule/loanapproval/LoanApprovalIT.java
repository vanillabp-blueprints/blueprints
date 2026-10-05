package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have reached the user task, answers it and waits again.
 *
 * <p>
 * One test per way a user task ends, because that is the aspect of this blueprint. Each of
 * them asserts on the workflow aggregate, never on the engine.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  @Test
  public void theUserTaskReportsItsIdAndTheWorkflowWaits() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null);

    // The service task ahead of the user task ran, the one behind it did not: the workflow
    // stays at the user task until the application answers.
    assertThat(loanRequest.getCreditRating()).isEqualTo(50);
    assertThat(loanRequest.getCustomerInformed()).isNull();

  }

  @Test
  public void completingTheUserTaskLetsTheWorkflowContinue() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var taskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null)
        .getRiskAssessmentTaskId();

    loanApproval.assessRisk(loanRequestId, taskId, true);

    // The service task behind the user task ran, so the workflow left the user task
    // through its regular sequence flow.
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanRequest.getRiskAcceptable()).isTrue();
    assertThat(loanRequest.getWithdrawn()).isNull();
    assertThat(loanRequest.getRiskAssessmentTaskId()).isNull();

  }

  /**
   * Canceling a user task is not supported by every BPMS: Camunda 8 has no command for it
   * up to and including version 8.8, and VanillaBP says so with an error rather than
   * pretending. This test therefore runs on Camunda 7, and the Maven profile choosing the
   * BPMS is what tells it apart.
   */
  @Test
  @EnabledIfSystemProperty(named = "blueprint.bpms", matches = "camunda7")
  public void cancelingTheUserTaskTakesTheErrorPath() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var taskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null)
        .getRiskAssessmentTaskId();

    loanApproval.withdrawLoanRequest(loanRequestId, taskId);

    // The service task on the error path ran, so the workflow left the user task through
    // the error boundary event.
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getWithdrawn()));

    assertThat(loanRequest.getCustomerInformed()).isNull();
    // The handler was called a second time, with TaskEvent CANCELED, and dropped the id of
    // the task nobody can answer any more.
    assertThat(loanRequest.getRiskAssessmentTaskId()).isNull();

  }

}
