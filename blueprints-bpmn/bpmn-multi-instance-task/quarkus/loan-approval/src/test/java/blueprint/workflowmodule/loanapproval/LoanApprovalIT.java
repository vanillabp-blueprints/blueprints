package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.PartnerOffer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for every iteration of the multi-instance task to have left its row.
 *
 * <p>
 * The assertions are about the rows, not about the order they appeared in. The instances
 * run at the same time, so anything relying on iteration 0 committing before iteration 1
 * would be a test which passes on one engine and fails on the other.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  private Aggregate runWith(
      final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, amount);

    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getChosenPartnerId() != null);

  }

  @Test
  @DisplayName("Every configured partner is asked exactly once")
  public void oneIterationPerPartner() {

    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getOffers())
        .describedAs("one row per iteration, none of them lost to a parallel sibling")
        .hasSize(3)
        .extracting(PartnerOffer::getPartnerId)
        .containsExactlyInAnyOrder("northern-bank", "harbour-credit", "alpine-savings");

    assertThat(loanApproval.getOffers())
        .extracting(PartnerOffer::getIteration)
        .describedAs("the index the BPMS counted, one per iteration")
        .containsExactlyInAnyOrder(0, 1, 2);

  }

  @Test
  @DisplayName("The task after the iterations picks the best offer")
  public void theBestOfferWins() {

    // a rating of 50 adds 50 basis points to every spread: 95, 70 and 85
    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    assertThat(loanApproval.getChosenPartnerId()).isEqualTo("harbour-credit");
    assertThat(loanApproval.getChosenRate()).isEqualTo(70);

  }

}
