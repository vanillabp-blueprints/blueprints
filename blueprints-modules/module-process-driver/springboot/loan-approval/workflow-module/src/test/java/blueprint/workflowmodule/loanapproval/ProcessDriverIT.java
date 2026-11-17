package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import blueprint.testdriver.GeneratedClients;
import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.TestData;
import blueprint.testdriver.loanapproval.steps.ProcessStateNotReachedException;
import blueprint.workflowmodule.processdriver.client.ProcessDriverApi;

/**
 * The process driver itself: its REST API, which the dev shell and the tests of a user interface
 * use, and the reason it gives when a state cannot be reached. The client of the REST API is
 * generated from {@code process-driver.yaml} for this test only.
 */
public class ProcessDriverIT extends ScenarioTest {

  private final ProcessDriverApi processDriver = GeneratedClients
      .at("http://localhost:"
          + PORT)
      .of(ProcessDriverApi.class);

  @Test
  public void itListsItsStates() {

    assertThat(processDriver.states())
        .containsExactly(
            "ASSESS_RISK_USER_TASK",
            "CHECK_CONTRACT_USER_TASK_WITHOUT_SIGNATURE",
            "CHECK_CONTRACT_USER_TASK",
            "CONTRACT_DECLINED");

  }

  /**
   * What the dev shell does when a person asks for a large loan whose contract waits for its
   * check. The driver assesses the risk on the way.
   */
  @Test
  public void itBringsALargeLoanToTheCheckOfItsSignedContract() {

    final var reached = processDriver.advanceTo("CHECK_CONTRACT_USER_TASK", TestData.LARGE_AMOUNT, null);

    assertThat(reached.state()).isEqualTo("CHECK_CONTRACT_USER_TASK");
    assertThat(loanApprovals.findById(reached.loanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(reached.userTaskId());
          assertThat(loanRequest.getCustomerId()).isEqualTo(reached.customerId());
          assertThat(loanRequest.getRiskAssessed()).isTrue();
          assertThat(loanRequest.getContractSigned()).isTrue();
        });

  }

  /**
   * What the dev shell does when a person wants to see a case whose customer declined to sign.
   * The workflow ended, so the driver names no open task.
   */
  @Test
  public void itBringsALoanToItsEndWhenTheCustomerDeclinesTheContract() {

    final var reached = processDriver.advanceTo("CONTRACT_DECLINED", null, null);

    assertThat(reached.state()).isEqualTo("CONTRACT_DECLINED");
    assertThat(reached.userTaskId()).as("no task is open").isNull();
    assertThat(loanApproval.show(reached.loanRequestId()))
        .satisfies(loanRequest -> {
          assertThat(loanRequest.customerName()).isEqualTo(TestData.JACK_NOSIGN);
          assertThat(loanRequest.loanDeclined()).isTrue();
        });

  }

  @Test
  public void anUnknownStateIsRefusedWithTheKnownOnes() {

    assertThatThrownBy(() -> processDriver.advanceTo("SOMEWHERE", null, null))
        .isInstanceOfSatisfying(
            HttpClientErrorException.BadRequest.class,
            refused -> assertThat(refused.getResponseBodyAsString()).contains("CHECK_CONTRACT_USER_TASK"));

  }

  @Test
  public void aStateTheTestDataCannotReachIsRefusedWithTheReason() {

    // The default test data names a small amount, which never leads to "Assess risk".
    assertThatThrownBy(() -> driver.advanceTo(ProcessState.ASSESS_RISK_USER_TASK, TestData.defaults()))
        .isInstanceOf(ProcessStateNotReachedException.class)
        .hasMessageContaining("Assess risk")
        .hasMessageContaining("does not ask for a risk assessment for the amount 5000");

  }

}
