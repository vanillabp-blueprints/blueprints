package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work.
 *
 * <p>
 * The aspect of this blueprint is a decision the BPMS makes: the decision table is
 * deployed with the process, the business rule task has the engine evaluate it, and what
 * it decided steers the model AND reaches the application. Both outcomes of the table are
 * run, because a decision which always says the same thing proves nothing.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Test
  public void theDecisionApprovesASmallLoan() {

    final var loanRequestId = UUID.randomUUID().toString();

    // a rating of 8, which the table approves
    service.initiateLoanApproval(loanRequestId, 2000);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getApproval() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(8);
    assertThat(loanApproval.getApproval()).isEqualTo("APPROVED");

  }

  @Test
  public void aDeclinedLoanNeverReachesTheApplication() {

    final var loanRequestId = UUID.randomUUID().toString();

    // a rating of 1 on a loan too big for the small-loan rule: the table declines, and
    // the gateway sends the workflow to the other end event - no Java runs there
    service.initiateLoanApproval(loanRequestId, 9000);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(1);
    assertThat(loanApproval.getApproval()).isNull();

  }

}
