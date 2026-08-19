package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * What each module's own test cannot show: the two of them in one application, doing to each
 * other what they were built for.
 *
 * <p>
 * A loan approval is started, and nothing else happens here. Its process asks the risk
 * assessment through the interface, that module runs a process of its own, publishes its
 * result, and the loan approval continues - two BPMN processes, two aggregates, two
 * databases tables, and not one line of code in either module naming the other.
 * </p>
 *
 * <p>
 * The test waits for the effect rather than for a step: what the interaction is worth is
 * that the answer arrives at all, and how many transactions and threads it took on the way
 * is the platform's business.
 * </p>
 */
@SpringBootTest
public class ModuleInteractionIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired
  private blueprint.workflowmodule.loanapproval.Service loanApprovals;

  @Autowired
  private AggregateRepository loanApprovalRepository;

  @Test
  public void theAnswerOfTheOtherModuleReachesTheWaitingProcess() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApprovals.initiateLoanApproval(loanRequestId, 6000);

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(200))
        .until(() -> loanApprovalRepository
            .findById(loanRequestId)
            .map(aggregate -> aggregate.getRiskScore() != null)
            .orElse(false));

    final var loanApproval = loanApprovalRepository.findById(loanRequestId).orElseThrow();

    assertThat(loanApproval.getCreditRating()).isEqualTo(60);
    assertThat(loanApproval.getRiskScore())
        .describedAs("what the other module worked out, carried by an event and a message")
        .isEqualTo(30);

  }

}
