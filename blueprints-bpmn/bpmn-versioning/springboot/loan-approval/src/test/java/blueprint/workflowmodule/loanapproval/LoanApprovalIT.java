package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work.
 *
 * <p>
 * The engine of this test is empty, so the model deploys as version 1 and the method serving
 * that version is the one which runs. That is the case this blueprint is about: a workflow
 * keeps the code of the version it was started on, and the assertion below proves the
 * dispatch happens rather than describing it.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Test
  @DisplayName("The method serving the deployed version is the one which runs")
  public void theMethodOfTheDeployedVersionRuns() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getAssessedBy())
        .describedAs("the method serving version 1 ran, because that is what was deployed")
        .isEqualTo("the four eyes principle");
    assertThat(loanApproval.getRiskScore())
        .describedAs("the method of the later versions did not run")
        .isNull();
    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

}
