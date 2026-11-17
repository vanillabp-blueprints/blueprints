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
   * A customer who declines to sign ends the loan request. There is nothing left for the clerk to
   * check, so the workflow takes the task "Check contract" away, and the loan is declined.
   */
  @Test
  public void aCustomerWhoDeclinesToSignEndsTheLoanRequestDeclined() {

    // The driver chooses Jack Nosign for this state, and waits until the loan is declined.
    final var scenario = driver.advanceTo(ProcessState.CONTRACT_DECLINED);

    assertThat(scenario.getTestData().customerName()).isEqualTo(TestData.JACK_NOSIGN);
    assertThat(simulator().callbacks(scenario.getLoanRequestId()))
        .containsExactly(new SentCallback(scenario.getLoanRequestId(), SignatureStatus.DECLINED, REPORT_TAKEN));
    assertThat(loanApproval.show(scenario.getLoanRequestId()))
        .satisfies(loanRequest -> {
          assertThat(loanRequest.contractDeclined()).isTrue();
          assertThat(loanRequest.loanDeclined()).isTrue();
          assertThat(loanRequest.contractSigned()).as("signed").isNull();
          assertThat(loanRequest.contractChecked()).as("approved").isNull();
          assertThat(loanRequest.checkContractTaskId()).as("the task of the clerk").isNull();
        });

  }

  /**
   * The customer declines while the clerk's task is open. The clerk loses the task: the cockpit
   * takes it off the list, because the workflow ended. A second report of the same decline, which
   * a service may send after it lost the first answer, changes nothing.
   */
  @Test
  public void aDeclineTakesTheTaskAwayFromTheClerkOnce() {

    final var scenario = driver.advanceTo(ProcessState.CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE);
    final var taskId = driver.openUserTaskId(scenario);

    assertThat(theCustomerDeclines(scenario.getLoanRequestId())).isEqualTo(REPORT_TAKEN);

    awaitUserTask(taskId, task -> task.getEndedAt() != null);
    awaitAggregate(
        loanApprovals,
        scenario.getLoanRequestId(),
        loanRequest -> Boolean.TRUE.equals(loanRequest.getLoanDeclined()));
    assertThat(theCustomerDeclines(scenario.getLoanRequestId())).as("the application took the second report")
        .isEqualTo(REPORT_TAKEN);
    assertThat(loanApproval.show(scenario.getLoanRequestId()))
        .satisfies(loanRequest -> {
          assertThat(loanRequest.loanDeclined()).isTrue();
          assertThat(loanRequest.checkContractTaskId()).isNull();
        });

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

  private static int theCustomerDeclines(
      final String contractId) {

    return simulator()
        .sendCallback(contractId, SignatureStatus.DECLINED, CALLBACK_URL)
        .answer();

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
