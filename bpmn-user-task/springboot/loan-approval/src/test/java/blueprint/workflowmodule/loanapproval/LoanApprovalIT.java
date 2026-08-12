package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have reached the user task, answers it and waits again.
 *
 * <p>
 * One test per way a user task ends, because that is the aspect of this blueprint. Each of
 * them asserts on the workflow aggregate, never on the engine.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Test
  public void theUserTaskReportsItsIdAndTheWorkflowWaits() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null);

    // The service task ahead of the user task ran, the one behind it did not: the workflow
    // stays at the user task until the application answers.
    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    assertThat(loanApproval.getCustomerInformed()).isNull();

  }

  @Test
  public void completingTheUserTaskLetsTheWorkflowContinue() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var taskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null)
        .getRiskAssessmentTaskId();

    service.assessRisk(loanRequestId, taskId, true);

    // The service task behind the user task ran, so the workflow left the user task
    // through its regular sequence flow.
    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getRiskAcceptable()).isTrue();
    assertThat(loanApproval.getWithdrawn()).isNull();
    assertThat(loanApproval.getRiskAssessmentTaskId()).isNull();

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

    service.initiateLoanApproval(loanRequestId, 5000);

    final var taskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null)
        .getRiskAssessmentTaskId();

    service.withdrawLoanRequest(loanRequestId, taskId);

    // The service task on the error path ran, so the workflow left the user task through
    // the error boundary event.
    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getWithdrawn()));

    assertThat(loanApproval.getCustomerInformed()).isNull();
    // The handler was called a second time, with TaskEvent CANCELED, and dropped the id of
    // the task nobody can answer any more.
    assertThat(loanApproval.getRiskAssessmentTaskId()).isNull();

  }

}
