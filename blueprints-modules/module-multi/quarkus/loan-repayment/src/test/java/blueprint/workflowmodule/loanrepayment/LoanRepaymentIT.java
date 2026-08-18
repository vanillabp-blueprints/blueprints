package blueprint.workflowmodule.loanrepayment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanrepayment.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of the second workflow module, and it is deliberately unaware of the
 * first one: a workflow module is tested against its own JAR, with a BPMS and a database
 * and nothing else. That the two modules also work together is the application's test, not
 * this one's.
 */
@QuarkusTest
public class LoanRepaymentIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository repayments;

  @Test
  public void theServiceTaskFillsTheAggregate() {

    final var repaymentId = UUID.randomUUID().toString();

    service.initiateRepayment(repaymentId, "C-1002", 6000);

    final var repayment = awaitAggregate(
        repayments::findByIdOptional,
        repaymentId,
        aggregate -> aggregate.getInstallments() != null);

    assertThat(repayment.getInstallments()).isEqualTo(6);

  }

}
