package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of the loan approval: a workflow through both of its wait states.
 *
 * <p>
 * It runs unchanged against one BPMS and against two, which is the promise of this
 * blueprint. Which BPMS answered is asserted by {@code MigrationIT}, where the answer is
 * the subject; here the aggregate is, as everywhere else.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Test
  @DisplayName("A loan approval passes the user task and the message")
  public void theWorkflowRunsThroughBothWaitStates() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var waitingForAssessment = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null);
    assertThat(waitingForAssessment.getCreditRating()).isEqualTo(50);

    service.assessRisk(loanRequestId, waitingForAssessment.getRiskAssessmentTaskId());

    // the user task is answered, so the workflow waits for the message now
    final var waitingForContract = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() == null);
    assertThat(waitingForContract.getRiskAcceptable()).isTrue();
    assertThat(waitingForContract.getPaidOut()).isNull();

    service.contractSigned(loanRequestId, "Jane Doe");

    final var paidOut = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));
    assertThat(paidOut.getContractSignedBy()).isEqualTo("Jane Doe");

  }

}
