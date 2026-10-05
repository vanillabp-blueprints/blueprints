package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * What each module's own test cannot show: the two of them in one application, doing to each
 * other what they were built for.
 *
 * <p>
 * A loan approval is started, and nothing else happens here. Its process asks the risk
 * assessment through the interface, that module runs a process of its own, publishes its
 * result, and the loan approval continues - two BPMN processes, two aggregates, two database
 * tables, and not one line of code in either module naming the other.
 * </p>
 */
@QuarkusTest
public class ModuleInteractionIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Inject
  blueprint.workflowmodule.loanapproval.Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  @Test
  public void theAnswerOfTheOtherModuleReachesTheWaitingProcess() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 6000);

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(200))
        .until(() -> QuarkusTransaction
            .requiringNew()
            .call(() -> loanApprovals
                .findByIdOptional(loanRequestId)
                .map(aggregate -> aggregate.getRiskScore() != null)
                .orElse(false)));

    final var loanRequest = QuarkusTransaction
        .requiringNew()
        .call(() -> loanApprovals.findByIdOptional(loanRequestId).orElseThrow());

    assertThat(loanRequest.getCreditRating()).isEqualTo(60);
    assertThat(loanRequest.getRiskScore())
        .describedAs("what the other module worked out, carried by an event and a message")
        .isEqualTo(30);

  }

}
