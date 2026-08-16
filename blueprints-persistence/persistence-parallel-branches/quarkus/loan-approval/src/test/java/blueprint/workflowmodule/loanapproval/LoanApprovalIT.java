package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module. It does what a race condition test has to
 * do: it produces the race instead of waiting for it.
 *
 * <p>
 * The document branch is held inside its transaction, the partner branch is answered while
 * it is held, and only then is the document branch let go. Both transactions therefore
 * overlap in every run, on every machine, and the assertion is that neither result is gone.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  DocumentsSimulator documents;

  @BeforeEach
  public void forgetWhatThePreviousTestDid() {

    documents.reset();

  }

  @Test
  @DisplayName("Both branches keep their result when they run at the same time")
  public void bothBranchesKeepTheirResult() {

    documents.hold();

    final var loanRequestId = UUID.randomUUID().toString();
    service.initiateLoanApproval(loanRequestId, 5000);

    // the document branch is now inside its transaction and stays there
    documents.awaitInvocation("collect "
        + loanRequestId);

    // ... while the other branch is answered in a transaction of the application
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanApproval -> (loanApproval.getPartnerApproval() != null) && (loanApproval
            .getPartnerApproval()
            .getTaskId() != null));
    service.approvePartnerRequest(loanRequestId, "partner");

    documents.letGo();

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        candidate -> Boolean.TRUE.equals(candidate.getCustomerInformed()));

    assertThat(loanApproval.getPartnerApproval().getApprovedBy())
        .describedAs("what the branch of the application wrote")
        .isEqualTo("partner");
    assertThat(loanApproval.getDocumentCheck().getDocumentsReceived())
        .describedAs("what the branch of the BPMS wrote, committed after the other one")
        .isEqualTo(3);
    assertThat(loanApproval.getCreditRating())
        .describedAs("what was written before the split, which neither branch touches")
        .isEqualTo(50);

  }

  @Test
  @DisplayName("The workflow ends once both branches joined")
  public void theWorkflowEndsOnceBothBranchesJoined() {

    final var loanRequestId = UUID.randomUUID().toString();
    service.initiateLoanApproval(loanRequestId, 5000);

    // no holding this time: the branches run in whatever order the BPMS picks
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanApproval -> (loanApproval.getPartnerApproval() != null) && (loanApproval
            .getPartnerApproval()
            .getTaskId() != null));
    service.approvePartnerRequest(loanRequestId, "partner");

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        candidate -> Boolean.TRUE.equals(candidate.getCustomerInformed()));

    assertThat(loanApproval.getDocumentCheck())
        .describedAs("the join waits for both branches, so this cannot be empty")
        .isNotNull();
    assertThat(documents.invocations())
        .describedAs("the document service is asked once per workflow")
        .containsExactly("collect "
            + loanRequestId);

  }

}
