package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.Check;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the ad-hoc subprocess to have run the activities somebody picked.
 *
 * <p>
 * One test per decision maker, because that is the aspect of this blueprint: the same
 * element serves a choice made by a person and a choice made by a decision table, and the
 * model does not know which of them it was. Each test asserts on the workflow aggregate,
 * never on the engine, and waits rather than asserting immediately.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  @Test
  public void theCaseWorkerPicksTwoOfThreeChecks() {

    final var loanRequestId = UUID.randomUUID().toString();

    // above ten thousand a person has to look at the request
    service.initiateLoanApproval(loanRequestId, 20000);

    final var taskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getSelectChecksTaskId() != null)
        .getSelectChecksTaskId();

    service.selectChecks(loanRequestId, taskId, List.of(Check.INCOME, Check.COLLATERAL));

    // the service task behind the subprocess ran, so the workflow left the element - which
    // it does when everything it activated is done, with no completion condition modelled
    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getIncomeChecked()).isTrue();
    assertThat(loanApproval.getCollateralChecked()).isTrue();
    // the third activity is in the model and was not picked, so it never ran
    assertThat(loanApproval.getFraudChecked()).isNull();
    assertThat(loanApproval.getChecksToRun())
        .containsExactly("Check_Income", "Check_Collateral");
    assertThat(loanApproval.getSelectChecksTaskId()).isNull();

  }

  @Test
  public void theDecisionTablePicksWithoutAnybodyLookingAtIt() {

    final var loanRequestId = UUID.randomUUID().toString();

    // below ten thousand nobody is asked: the decision table picks, and it picks the income
    // check for every request plus the fraud check above three thousand
    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getIncomeChecked()).isTrue();
    assertThat(loanApproval.getFraudChecked()).isTrue();
    assertThat(loanApproval.getCollateralChecked()).isNull();
    // no user task was created, and no Java wrote the list: the decision table's result
    // went straight into the variable the model reads
    assertThat(loanApproval.getSelectChecksTaskId()).isNull();
    assertThat(loanApproval.getChecksToRun()).isEmpty();

  }

}
