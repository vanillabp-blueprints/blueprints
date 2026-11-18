package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blueprint.testdriver.GeneratedClients;
import blueprint.testdriver.loanapproval.ProcessState;
import blueprint.testdriver.loanapproval.TestData;
import blueprint.testdriver.loanapproval.steps.ProcessStateNotReachedException;
import blueprint.workflowmodule.processdriver.client.ProcessDriverApi;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * The process driver itself: its REST API, which the dev shell and the tests of a user interface
 * use, and the reason it gives when a state cannot be reached. The client of the REST API is
 * generated from {@code process-driver.yaml} for this test only.
 */
@QuarkusTest
@WithTestResource(EverySystemIsTheSimulator.class)
public class ProcessDriverIT extends ScenarioTest {

  /** How the driver answers a state it does not know. */
  private static final int UNKNOWN_STATE = Response.Status.BAD_REQUEST.getStatusCode();

  /** The address of the application under test, which Quarkus knows. */
  @TestHTTPResource("/")
  URI application;

  private ProcessDriverApi processDriver;

  @BeforeEach
  public void createClient() {

    processDriver = GeneratedClients
        .at(application.toString())
        .of(ProcessDriverApi.class);

  }

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

    assertThat(reached.getState()).isEqualTo("CHECK_CONTRACT_USER_TASK");
    assertThat(loanApprovals.findByIdOptional(reached.getLoanRequestId()))
        .hasValueSatisfying(loanRequest -> {
          assertThat(loanRequest.getCheckContractTaskId()).isEqualTo(reached.getUserTaskId());
          assertThat(loanRequest.getCustomerId()).isEqualTo(reached.getCustomerId());
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

    assertThat(reached.getState()).isEqualTo("CONTRACT_DECLINED");
    assertThat(reached.getUserTaskId()).as("no task is open").isNull();
    assertThat(loanApproval.show(reached.getLoanRequestId()))
        .satisfies(loanRequest -> {
          assertThat(loanRequest.getCustomerName()).isEqualTo(TestData.JACK_NOSIGN);
          assertThat(loanRequest.getLoanDeclined()).isTrue();
        });

  }

  @Test
  public void anUnknownStateIsRefusedWithTheKnownOnes() {

    assertThatThrownBy(() -> processDriver.advanceTo("SOMEWHERE", null, null))
        .isInstanceOfSatisfying(
            WebApplicationException.class,
            refused -> {
              assertThat(refused
                  .getResponse()
                  .getStatus()).isEqualTo(UNKNOWN_STATE);
              assertThat(refused
                  .getResponse()
                  .readEntity(String.class)).contains("CHECK_CONTRACT_USER_TASK");
            });

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
