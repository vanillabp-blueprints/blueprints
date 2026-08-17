package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS and
 * waits for the process to have done its work.
 *
 * <p>
 * The aggregate is read through its own static finder, which is the whole point of this
 * blueprint: no repository is injected anywhere, not even here. What that costs is visible in
 * {@link WorkflowModuleTest}, which reads inside a transaction of its own per poll - without
 * one, an active record has nothing to read from.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Test
  public void theServiceTaskFillsTheAggregate() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        Aggregate::byId,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

  /**
   * The assertion this blueprint needs beyond the base blueprint: an aggregate which stores
   * itself survives being started on a remote BPMS.
   *
   * <p>
   * Starting a workflow on a remote engine happens in two phases. The application's
   * transaction stores the aggregate and the intent to start; afterwards VanillaBP talks to
   * the engine and writes the result of that back, on a thread of its own. Nothing of the
   * application is on the stack at that moment, so with no repository there would be no class
   * left to declare a transaction on - VanillaBP opens one itself. That is what this test
   * covers, and it only covers it on a remote engine: run it with '-Pcamunda8'.
   * </p>
   */
  @Test
  public void theStartedWorkflowKeepsItsAggregate() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 12_000);

    final var loanApproval = awaitAggregate(Aggregate::byId, loanRequestId);

    assertThat(loanApproval.getAmount()).isEqualTo(12_000);

  }

}
