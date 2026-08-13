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
 * and waits for the process to have taken one of the three branches.
 *
 * <p>
 * One test per branch, because the branches are the aspect of this blueprint. What steers
 * them is the amount, which the business code turns into a rating and the rating into the
 * two attributes the gateway reads.
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
        aggregate -> aggregate.getOutcome() != null);

  }

  @Test
  @DisplayName("A rating at or above the minimum takes the first branch")
  public void anAcceptableRatingIsApproved() {

    // 5000 / 100 is a rating of 50, the configured minimum is 30
    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    assertThat(loanApproval.getRatingBand()).isEqualTo("acceptable");
    assertThat(loanApproval.getOutcome()).isEqualTo("approved");

  }

  @Test
  @DisplayName("A rating between the two thresholds takes the second branch")
  public void aMiddlingRatingGoesToAManualReview() {

    // a rating of 15: below the minimum of 30, at or above the review rating of 10
    final var loanApproval = runWith(1500);

    assertThat(loanApproval.getRatingBand()).isEqualTo("review");
    assertThat(loanApproval.getOutcome()).isEqualTo("under-review");

  }

  @Test
  @DisplayName("A rating below both thresholds takes the default flow")
  public void aBadRatingIsRejected() {

    // a rating of 3: no condition of the gateway holds, so the default flow is taken
    final var loanApproval = runWith(300);

    assertThat(loanApproval.getRatingBand()).isEqualTo("too-low");
    assertThat(loanApproval.getOutcome()).isEqualTo("rejected");

  }

}
