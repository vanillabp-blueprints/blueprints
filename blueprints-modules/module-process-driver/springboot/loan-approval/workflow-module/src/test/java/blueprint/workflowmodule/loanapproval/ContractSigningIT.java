package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

import blueprint.simulator.testapi.client.model.SentCallback;
import blueprint.simulator.testapi.client.model.SignatureStatus;
import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.TestData;

/**
 * What the application does when the e-signature service behaves like a real one: the customer
 * signs, declines, or does nothing, or the service fails. The simulator chooses the behaviour by
 * the name of the customer, who signs the contract. So each test says its case with test data,
 * and needs no call to set the simulator up.
 */
public class ContractSigningIT extends ScenarioTest {

  @Test
  public void aCustomerWhoSignsByHimselfLetsTheClerkApproveTheLoan() {

    // The driver chooses John Signing for this state, and waits for his signature.
    final var scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK);

    assertThat(scenario.getTestData().customerName()).isEqualTo(TestData.JOHN_SIGNING);
    assertThat(simulator().callbacks(scenario.getLoanRequestId()))
        .containsExactly(new SentCallback(scenario.getLoanRequestId(), SignatureStatus.SIGNED, REPORT_TAKEN));

    loanApproval.completeCheckContract(scenario.getLoanRequestId(), driver.openUserTaskId(scenario));

    awaitAggregate(
        loanApprovals,
        scenario.getLoanRequestId(),
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractChecked()));

  }

  /**
   * The module knows no declined contract. Its port of the e-signature service knows only
   * signatures. So the application does not take the report, the contract stays unsigned, and
   * the clerk cannot approve the loan.
   */
  @Test
  public void aContractTheCustomerDeclinedStaysUnsigned() {

    final var scenario = driver.advanceTo(
        ProcessState.CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE,
        TestData
            .defaults()
            .withCustomerName(TestData.JACK_NOSIGN));
    final var taskId = driver.openUserTaskId(scenario);

    await()
        .atMost(TIMEOUT)
        .until(() -> simulator().callbacks(scenario.getLoanRequestId()), callbacks -> !callbacks.isEmpty());
    assertThat(simulator().callbacks(scenario.getLoanRequestId()))
        .containsExactly(new SentCallback(scenario.getLoanRequestId(), SignatureStatus.DECLINED, REPORT_NOT_TAKEN));

    assertThat(loanApprovals.findById(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> assertThat(loanRequest.getContractSigned()).isNull());
    assertThat(reasonOfTheRefusal(() -> loanApproval.completeCheckContract(scenario.getLoanRequestId(), taskId)))
        .contains("not signed yet");

  }

  /**
   * A customer who never signs leaves the task open. The module models no deadline for the
   * signature, so nothing else happens.
   */
  @Test
  public void aContractNobodySignsKeepsTheTaskOpen() {

    final var scenario = driver.advanceTo(
        ProcessState.CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE,
        TestData
            .defaults()
            .withCustomerName(TestData.JIM_AVOIDANCE));
    final var taskId = driver.openUserTaskId(scenario);

    staysSoForAWhile(() -> simulator()
        .callbacks(scenario.getLoanRequestId())
        .isEmpty());

    assertThat(loanApprovals.findById(scenario.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getContractSigned()).isNull();
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(taskId);
        });
    assertThat(reasonOfTheRefusal(() -> loanApproval.completeCheckContract(scenario.getLoanRequestId(), taskId)))
        .contains("not signed yet");

  }

  /**
   * The service task "Send contract for signing" fails, and the BPMS treats it like every task
   * which fails: it may try again, and then it shows an incident to an operator. The module
   * models nothing else. So the contract never counts as sent, and no clerk gets a task.
   */
  @Test
  public void aFailingESignatureServiceStopsTheContractFromGoingOut() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, TestData.GOOD_CREDIT_SCORE);

    final var answer = loanApproval.request(customerId, TestData.JANE_ERRORNOUS, TestData.SMALL_AMOUNT);

    assertThat(answer.outcome()).isEqualTo("started");
    await()
        .atMost(TIMEOUT)
        .until(() -> simulator().refusals(answer.loanRequestId()), refusals -> refusals > 0);
    staysSoForAWhile(() -> loanApprovals
        .findById(answer.loanRequestId())
        .map(loanRequest -> loanRequest.getContractSentForSigning() == null && loanRequest
            .getCheckContractTaskId() == null)
        .orElse(false));
    assertThat(simulator().envelope(answer.loanRequestId())).isEmpty();

  }

}
