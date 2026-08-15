package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.PartnerOffer;
import blueprint.workflowmodule.loanapproval.model.RegionResult;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits until every region has been assessed and every partner asked in each of them.
 *
 * <p>
 * Two loops means regions times partners rows, and no order between them. The assertions
 * are therefore about what is there, not about when it arrived - the iterations run next to
 * each other, and a test expecting a sequence passes on an embedded engine and fails on a
 * remote one.
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
        aggregate -> aggregate.getChosenPartnerId() != null);

  }

  @Test
  @DisplayName("the subprocess runs per region and the task inside it per partner")
  public void bothLoopsRun() {

    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getOffers())
        .describedAs("two regions times two partners, none of them lost to a sibling")
        .hasSize(4)
        .extracting(PartnerOffer::getRegionId, PartnerOffer::getPartnerId, PartnerOffer::getRate)
        .containsExactlyInAnyOrder(
            // a rating of 50 adds 50 basis points, the region adds its surcharge
            tuple("north", "northern-bank", 70),
            tuple("north", "harbour-credit", 85),
            tuple("south", "northern-bank", 85),
            tuple("south", "harbour-credit", 100));

  }

  @Test
  @DisplayName("a plain task inside the subprocess knows which iteration it belongs to")
  public void eachRegionIsSummarisedOnce() {

    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getRegionResults())
        .hasSize(2)
        .extracting(RegionResult::getRegionId, RegionResult::getBestPartnerId, RegionResult::getBestRate)
        .containsExactlyInAnyOrder(
            tuple("north", "northern-bank", 70),
            tuple("south", "northern-bank", 85));

    assertThat(loanApproval.getRegionResults())
        .extracting(RegionResult::getIteration)
        .describedAs("the index of the subprocess iteration, one per region")
        .containsExactlyInAnyOrder(0, 1);

  }

  @Test
  @DisplayName("the task after the subprocess decides over all iterations")
  public void theBestRegionWins() {

    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getChosenRegionId()).isEqualTo("north");
    assertThat(loanApproval.getChosenPartnerId()).isEqualTo("northern-bank");
    assertThat(loanApproval.getChosenRate()).isEqualTo(70);

  }

}
