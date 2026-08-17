package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS and
 * waits for the process to have done its work.
 *
 * <p>
 * The first test is the one of the base blueprint, unchanged except for the database
 * underneath. The second one is why this blueprint exists: it proves that the three stores of
 * a MongoDB application really share one transaction. That is the assertion which did not hold
 * before the framework taught its MongoDB stores to join the session.
 * </p>
 */
@SpringBootTest
public class LoanApprovalIT extends WorkflowModuleTest {

  /**
   * VanillaBP's own store for the second phase of a workflow start. A test normally has no
   * business looking in there, and this one does because the subject of the blueprint is where
   * those stores live.
   */
  private static final String OUTBOX_COLLECTION = "vanillabp-phase-two-outbox";

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private TransactionTemplate transactions;

  @Autowired
  private MongoTemplate mongoDb;

  @Test
  public void theServiceTaskFillsTheAggregate() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

  @Test
  public void aFailedStartLeavesNothingBehind() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var outboxEntriesBefore = mongoDb
        .getCollection(OUTBOX_COLLECTION)
        .countDocuments();

    // The application opens the transaction, VanillaBP joins it: the aggregate and the
    // entry scheduling the second phase of the start are written inside it. Then the
    // application decides the business case does not happen after all.
    assertThatThrownBy(
        () -> transactions.executeWithoutResult(status -> {
          service.initiateLoanApproval(loanRequestId, 5000);
          throw new IllegalStateException("the application aborts after the start");
        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(loanApprovals.findById(loanRequestId))
        .describedAs("the aggregate of a rolled-back start")
        .isEmpty();
    assertThat(mongoDb.getCollection(OUTBOX_COLLECTION).countDocuments())
        .describedAs("the outbox entry of a rolled-back start")
        .isEqualTo(outboxEntriesBefore);

  }

}
