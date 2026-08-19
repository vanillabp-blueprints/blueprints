package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import blueprint.workflowmodule.loanapproval.Service;

/**
 * A workflow runs on a schema nothing created at runtime.
 *
 * <p>
 * The workflow module has an integration test of its own, so why here again: the handover of
 * the schema only exists in the application. This test runs the process where every table came
 * from a migration, VanillaBP writes its outbox entries and delivery records into tables it
 * did not create, and the engine works on tables it did not create either. A migration which
 * describes a table with a wrong column would come out here rather than in production.
 * </p>
 */
@SpringBootTest
public class WorkflowOnTheOwnSchemaIT {

  /**
   * Long enough for the engine's scheduler, which shares this machine with every other module
   * of a full build.
   */
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  @Autowired
  private Service service;

  @Test
  public void theProcessRunsThrough() throws Exception {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var deadline = Instant.now().plus(TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      final var creditRating = service
          .getLoanApproval(loanRequestId)
          .map(aggregate -> aggregate.getCreditRating())
          .orElse(null);
      if (creditRating != null) {
        assertThat(creditRating).isEqualTo(50);
        return;
      }
      Thread.sleep(200);
    }

    assertThat(service.getLoanApproval(loanRequestId))
        .describedAs(
            "The service task did not fill the aggregate within "
                + TIMEOUT
                + ". Either the process never reached the task, or a table it needs is not the"
                + " one the migration built.")
        .isEmpty();

  }

}
