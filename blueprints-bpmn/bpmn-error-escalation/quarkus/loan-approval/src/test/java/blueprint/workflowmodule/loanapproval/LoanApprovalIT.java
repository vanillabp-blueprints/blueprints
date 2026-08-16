package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: one test per way out. The error ends the
 * task it was raised in, the escalation does not - and both are asserted on the workflow
 * aggregate.
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  @Test
  @DisplayName("The escalation reports upwards while the subprocess carries on")
  public void theEscalationLeavesTheSubprocessRunning() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getContractSigned()) && Boolean.TRUE
            .equals(aggregate.getSupervisorInformed()));

    // both happened: the branch behind the escalation ran, and so did the task after the
    // throw event inside the subprocess
    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    assertThat(loanApproval.getDocumentsRequested()).isNull();

  }

  @Test
  @DisplayName("The BPMN error ends the task and takes the error path")
  public void theErrorEndsTheTask() {

    final var loanRequestId = UUID.randomUUID().toString();

    // below the configured amount the documents count as incomplete
    service.initiateLoanApproval(loanRequestId, 500);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getDocumentsRequested()));

    // the subprocess was never entered
    assertThat(loanApproval.getCreditRating()).isNull();
    assertThat(loanApproval.getContractSigned()).isNull();
    assertThat(loanApproval.getSupervisorInformed()).isNull();

  }

}
