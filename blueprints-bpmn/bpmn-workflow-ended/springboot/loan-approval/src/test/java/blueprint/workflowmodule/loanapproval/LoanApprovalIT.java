package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the application to have been told that it ended.
 *
 * <p>
 * The second test is the one that matters. Its branch has no task in front of the end
 * event, so nothing but the end notification can write the closing time - if it does not
 * arrive, nothing else will.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  private Aggregate runWith(
      final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, amount);

    return awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getClosedAt() != null);

  }

  @Test
  @DisplayName("the end is reported although a task already ran in front of that end event")
  public void theEndOfTheBranchWithATask() {

    // 5000 / 100 is a rating of 50, the configured minimum is 30
    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getCustomerInformed())
        .describedAs("the task in front of the end event did its work")
        .isTrue();
    assertThat(loanApproval.getClosedAt())
        .describedAs("and the end was reported on top of it")
        .isNotNull();
    assertThat(loanApproval.getClosedBy()).isEqualTo("COMPLETED");

  }

  @Test
  @DisplayName("the end is reported where nothing is modelled in front of the end event")
  public void theEndOfTheBranchWithoutATask() {

    // a rating of 3, so the process takes the branch which goes straight to its end event
    final var loanApproval = runWith(300);

    assertThat(loanApproval.getCustomerInformed())
        .describedAs("no task ran on this branch")
        .isNull();
    assertThat(loanApproval.getClosedAt())
        .describedAs("the application still learns that the case is closed")
        .isNotNull();
    assertThat(loanApproval.getClosedBy()).isEqualTo("COMPLETED");

  }

}
