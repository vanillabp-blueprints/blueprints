package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

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
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  private Aggregate runWith(
      final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount);

    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> (aggregate.getOutcome() != null) && (!"approved"
            .equals(aggregate.getOutcome()) || (aggregate.getNotifiedBy() != null)));

  }

  @Test
  @DisplayName("A rating at or above the minimum takes the first branch")
  public void anAcceptableRatingIsApproved() {

    // 5000 / 100 is a rating of 50, the configured minimum is 30
    final var loanRequest = runWith(5000);

    assertThat(loanRequest.getCreditRating()).isEqualTo(50);
    assertThat(loanRequest.getRatingBand()).isEqualTo("acceptable");
    assertThat(loanRequest.isRatedAcceptable()).isTrue();
    assertThat(loanRequest.getOutcome()).isEqualTo("approved");
    // the second gateway, the one reading the raw amount: 5000 is below its threshold
    assertThat(loanRequest.getNotifiedBy()).isEqualTo("email");

  }

  @Test
  @DisplayName("The second gateway sends a letter for a large amount")
  public void aLargeAmountIsAnsweredByLetter() {

    final var loanRequest = runWith(50000);

    assertThat(loanRequest.getOutcome()).isEqualTo("approved");
    assertThat(loanRequest.getNotifiedBy()).isEqualTo("letter");

  }

  @Test
  @DisplayName("A rating between the two thresholds takes the second branch")
  public void aMiddlingRatingGoesToAManualReview() {

    // a rating of 15: below the minimum of 30, at or above the review rating of 10
    final var loanRequest = runWith(1500);

    assertThat(loanRequest.getRatingBand()).isEqualTo("review");
    assertThat(loanRequest.getOutcome()).isEqualTo("under-review");

  }

  @Test
  @DisplayName("A rating below both thresholds takes the default flow")
  public void aBadRatingIsRejected() {

    // a rating of 3: no condition of the gateway holds, so the default flow is taken
    final var loanRequest = runWith(300);

    assertThat(loanRequest.getRatingBand()).isEqualTo("too-low");
    assertThat(loanRequest.getOutcome()).isEqualTo("rejected");

  }

}
