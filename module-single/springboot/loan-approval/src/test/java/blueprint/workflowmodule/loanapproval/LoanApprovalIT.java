package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of the workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work.
 *
 * <p>
 * This is the level a blueprint proves its aspect on, and the level generated code has to
 * be verified on. Waiting instead of asserting immediately is not accidental: a BPMS
 * executes tasks in its own transactions, and remote ones do so eventually - a test which
 * assumes otherwise passes on one BPMS and fails on the next.
 * </p>
 */
@SpringBootTest
public class LoanApprovalIT {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Test
  public void theServiceTaskFillsTheAggregate() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> assertThat(
            loanApprovals
                .findById(loanRequestId)
                .orElseThrow()
                .getCreditRating())
                    .isEqualTo(50));

  }

}
