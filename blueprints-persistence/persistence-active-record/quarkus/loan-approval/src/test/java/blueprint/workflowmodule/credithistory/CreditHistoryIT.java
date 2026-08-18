package blueprint.workflowmodule.credithistory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.credithistory.model.Aggregate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of the second use case, and with it the test of the claim this
 * blueprint makes: two aggregates in one workflow module, each an active record, each in a
 * different database, and neither of them configured anywhere.
 *
 * <p>
 * The test is the loan approval's twin on purpose. Everything it does, the other one does too,
 * and the only difference is where the aggregate is read from - which is exactly how much the
 * choice of a persistence idiom is supposed to matter to the code above it.
 * </p>
 */
@QuarkusTest
public class CreditHistoryIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Test
  @DisplayName("The service task fills an aggregate stored in MongoDB")
  public void theServiceTaskFillsTheAggregate() {

    final var creditHistoryId = UUID.randomUUID().toString();

    service.requestCreditHistory(creditHistoryId, 3);

    final var creditHistory = awaitAggregate(
        Aggregate::byId,
        creditHistoryId,
        aggregate -> aggregate.getEntriesFound() != null);

    assertThat(creditHistory.getEntriesFound()).isEqualTo(12);

  }

  /**
   * The same assertion the loan approval makes about the two-phase start, for the aggregate
   * MongoDB stores.
   *
   * <p>
   * It is the sharper of the two: VanillaBP finishes the start on a thread of its own, opens a
   * transaction there, and for this aggregate that transaction has to reach into the MongoDB
   * session Panache registered - the aggregate, the outbox entry and the record of the
   * delivery all belong into it. Run it with '-Pcamunda8'.
   * </p>
   */
  @Test
  @DisplayName("A workflow started on a remote engine keeps its MongoDB aggregate")
  public void theStartedWorkflowKeepsItsAggregate() {

    final var creditHistoryId = UUID.randomUUID().toString();

    service.requestCreditHistory(creditHistoryId, 5);

    final var creditHistory = awaitAggregate(Aggregate::byId, creditHistoryId);

    assertThat(creditHistory.getYears()).isEqualTo(5);

  }

}
