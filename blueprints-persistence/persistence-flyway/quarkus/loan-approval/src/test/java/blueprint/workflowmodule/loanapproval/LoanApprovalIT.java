package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work.
 *
 * <p>
 * This is the level a blueprint proves its aspect on, and the level generated code has to
 * be verified on. Everything not specific to this blueprint - booting the module, waiting
 * for progress - comes from {@link WorkflowModuleTest}, so that what remains here is the
 * aspect and nothing else.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  @Test
  public void theServiceTaskFillsTheAggregate() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

}
