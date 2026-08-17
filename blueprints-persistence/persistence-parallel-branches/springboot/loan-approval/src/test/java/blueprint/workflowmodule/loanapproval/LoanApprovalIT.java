package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module. It does what a race condition test has to
 * do: it produces the race instead of waiting for it.
 *
 * <p>
 * The order is the trick. Both branches wait for the application first, so nothing runs
 * before the test says so. It then lets the document branch start and makes the document
 * service slow, which keeps that branch inside the transaction VanillaBP owns. The partner
 * branch is answered through the API while it lasts, so the two transactions overlap in
 * every run, on every machine.
 * </p>
 *
 * <p>
 * What the test does NOT do is block a task handler until it is released. A handler holds a
 * thread of the BPMS, and on a remote engine one adapter shares few of them: a handler
 * waiting for the test would keep the BPMS from delivering the very task the test is waiting
 * for.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  /** Long enough for an API call to happen inside it, short enough not to stall a build. */
  private static final Duration DOCUMENT_SERVICE_TAKES = Duration.ofSeconds(5);

  /** The surrounding system, replaced by a simulator the test can slow down. */
  @TestConfiguration
  static class Simulators {

    @Bean
    @Primary
    DocumentsSimulator documents() {

      return new DocumentsSimulator();

    }

  }

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private DocumentsSimulator documents;

  @BeforeEach
  public void forgetWhatThePreviousTestDid() {

    documents.reset();

  }

  private String startedWorkflowWaitingInBothBranches() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanApproval -> (loanApproval.getPartnerApproval() != null) && (loanApproval
            .getPartnerApproval()
            .getTaskId() != null) && (loanApproval.getDocumentCheck() != null) && (loanApproval
                .getDocumentCheck()
                .getTaskId() != null));

    return loanRequestId;

  }

  @Test
  @DisplayName("Both branches keep their result when they commit at the same time")
  public void bothBranchesKeepTheirResult() {

    final var loanRequestId = startedWorkflowWaitingInBothBranches();

    documents.takesAtLeast(DOCUMENT_SERVICE_TAKES);
    service.documentsReady(loanRequestId);

    // the document branch is inside the transaction VanillaBP owns and stays there ...
    documents.awaitInvocation("collect "
        + loanRequestId);

    // ... while the partner branch is answered in a transaction of the application
    service.approvePartnerRequest(loanRequestId, "partner");

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        candidate -> Boolean.TRUE.equals(candidate.getCustomerInformed()));

    assertThat(loanApproval.getPartnerApproval().getApprovedBy())
        .describedAs("what the branch of the application wrote")
        .isEqualTo("partner");
    assertThat(loanApproval.getDocumentCheck().getDocumentsReceived())
        .describedAs("what the branch of the BPMS wrote, committed after the other one")
        .isEqualTo(3);
    assertThat(loanApproval.getPartnerApproval().getApprovedAt())
        .describedAs("the answer was written while the other branch was still working")
        .isBefore(loanApproval.getDocumentCheck().getCollectedAt());
    assertThat(loanApproval.getCreditRating())
        .describedAs("what was written before the split, which neither branch touches")
        .isEqualTo(50);

  }

  @Test
  @DisplayName("The workflow ends once both branches joined")
  public void theWorkflowEndsOnceBothBranchesJoined() {

    final var loanRequestId = startedWorkflowWaitingInBothBranches();

    // no slowing down this time: the branches finish in whatever order the BPMS picks
    service.approvePartnerRequest(loanRequestId, "partner");
    service.documentsReady(loanRequestId);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        candidate -> Boolean.TRUE.equals(candidate.getCustomerInformed()));

    assertThat(loanApproval.getDocumentCheck().getDocumentsReceived())
        .describedAs("the join waits for both branches, so this cannot be empty")
        .isEqualTo(3);
    assertThat(documents.invocations())
        .describedAs("the document service is read once per workflow")
        .containsExactly("collect "
            + loanRequestId);

  }

}
