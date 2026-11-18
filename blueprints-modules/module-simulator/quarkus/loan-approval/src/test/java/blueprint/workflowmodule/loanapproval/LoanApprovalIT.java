package blueprint.workflowmodule.loanapproval;

import static blueprint.workflowmodule.loanapproval.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import blueprint.workflowmodule.SuppressOutputExtension;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.client.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * The integration test of this workflow module. It runs the module with its real adapters, and
 * every system around it is the simulator: the credit rating, the e-signature service and the
 * Business Cockpit. Nothing is mocked.
 *
 * <p>
 * The test acts like the people and systems around the application. It requests a loan over the
 * API, finds the user task in the cockpit, prepares the contract over the API, and signs the
 * contract through the test API of the simulator. It asserts on the workflow aggregate and on
 * what the simulated systems were told. It waits for the state of the application, never for a
 * fixed time.
 * </p>
 *
 * <p>
 * This module has no user interface. So these tests are its end-to-end tests as well: they go
 * through every interface the module has.
 * </p>
 */
@QuarkusTest
@WithTestResource(EverySystemIsTheSimulator.class)
// The output of a test is shown only if it fails, together with what the simulator logged
// meanwhile. So the suppression comes first. See SimulatorExtension for why the order matters.
@ExtendWith(SuppressOutputExtension.class)
@ExtendWith(SimulatorExtension.class)
public class LoanApprovalIT extends WorkflowModuleTest {

  /**
   * How the application answers a report of the e-signature service it took, as the callback
   * API says.
   */
  private static final int REPORT_TAKEN = Response.Status.NO_CONTENT.getStatusCode();

  /** How the application answers a report about a contract it does not know. */
  private static final int REPORT_OF_AN_UNKNOWN_CONTRACT = Response.Status.NOT_FOUND.getStatusCode();

  @Inject
  AggregateRepository loanApprovals;

  /** The address of the application under test, which Quarkus chose for this test run. */
  @TestHTTPResource("/")
  URI application;

  /** Where the application takes the reports of the e-signature service. */
  @TestHTTPResource("/e-signature/callback")
  URI callbackUrl;

  /**
   * The API of the module, through the client the build generates from its OpenAPI file. The
   * paths in the file are complete, so the client only needs the address of the application.
   */
  private LoanApprovalApi loanApproval;

  @BeforeEach
  public void createClient() {

    loanApproval = QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(application)
        .build(LoanApprovalApi.class);

  }

  @Test
  public void aLoanRequestGoesThroughAllThreeSystems() {

    // The credit rating knows the customer. The test reserves a customer of its own, so no other
    // test changes its score.
    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 720);

    final var loanRequestId = request(customerId, 5000);

    // The workflow started with the answer of the credit rating, and waits in the user task.
    final var prepareContractTaskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> loanRequest.getPrepareContractTaskId() != null)
        .getPrepareContractTaskId();
    assertThat(awaitAggregate(loanApprovals::findByIdOptional, loanRequestId).getCreditScore())
        .isEqualTo(720);

    // The cockpit shows the user task, with the details the module reported.
    final var userTask = simulator().awaitUserTask(prepareContractTaskId, task -> true);
    assertThat(userTask.getWorkflowModuleId()).isEqualTo("loan-approval");
    assertThat(userTask.getTaskDefinition()).isEqualTo("prepareContract");
    assertThat(userTask.getDetails())
        .isEqualTo(Map.of(
            WorkflowTaskHandler.AMOUNT, 5000,
            WorkflowTaskHandler.CREDIT_SCORE, 720));

    // Somebody prepares the contract, and the module hands it to the e-signature service.
    assertThat(loanApproval.prepareContract(loanRequestId, prepareContractTaskId, new BigDecimal("3.5")))
        .contains("prepared");
    final var sent = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSentForSigning()));
    assertThat(sent.getInterestRate()).isEqualByComparingTo(new BigDecimal("3.5"));
    assertThat(simulator().envelope(loanRequestId))
        .hasValueSatisfying(envelope -> {
          assertThat(envelope.getSigner()).isEqualTo(customerId);
          assertThat(envelope.getDocument()).contains("5000", "3.5");
        });

    // The customer signs. The simulator calls the application back, and the workflow ends.
    assertThat(simulator().sign(loanRequestId).getAnswer()).as("the application took the report")
        .isEqualTo(REPORT_TAKEN);
    final var signed = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()));
    assertThat(signed.getContractSigned()).isTrue();

    // The cockpit took the user task off the list.
    simulator().awaitUserTask(prepareContractTaskId, task -> task.getEndedAt() != null);

  }

  @Test
  public void aCustomerWithALowCreditScoreIsDeclinedAtOnce() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 480);

    final var answer = loanApproval.request(customerId, 5000);

    assertThat(answer.getOutcome()).isEqualTo("declined");
    assertThat(loanApprovals.findByIdOptional(answer.getLoanRequestId())).isEmpty();

  }

  @Test
  public void aCustomerTheCreditRatingDoesNotKnowIsDeclinedAtOnce() {

    // A reserved id the credit rating was never told about.
    final var customerId = simulator().nextCustomerId();

    final var answer = loanApproval.request(customerId, 5000);

    assertThat(answer.getOutcome()).isEqualTo("declined");
    assertThat(loanApprovals.findByIdOptional(answer.getLoanRequestId())).isEmpty();

  }

  @Test
  public void aSignatureIsTakenOnceEvenIfItArrivesTwice() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 650);
    final var loanRequestId = request(customerId, 1000);
    final var taskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> loanRequest.getPrepareContractTaskId() != null)
        .getPrepareContractTaskId();
    loanApproval.prepareContract(loanRequestId, taskId, new BigDecimal("4"));
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSentForSigning()));

    assertThat(simulator().sign(loanRequestId).getAnswer()).as("the application took the report")
        .isEqualTo(REPORT_TAKEN);
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()));

    // A service may report again, for example after it lost the first answer.
    assertThat(simulator().sign(loanRequestId).getAnswer()).as("the application took the report again")
        .isEqualTo(REPORT_TAKEN);

  }

  /**
   * The e-signature service reports a signature of a contract the application never sent, for
   * example because another system uses the same callback address. The simulator sends such a
   * report on request of its test API.
   */
  @Test
  public void aSignatureOfAnUnknownContractIsRefused() {

    final var unknownContract = UUID
        .randomUUID()
        .toString();

    final var report = simulator().sendCallback(unknownContract, callbackUrl.toString());

    assertThat(report.getAnswer()).as("the application does not know the contract")
        .isEqualTo(REPORT_OF_AN_UNKNOWN_CONTRACT);
    assertThat(loanApprovals.findByIdOptional(unknownContract)).isEmpty();

  }

  private String request(
      final String customerId,
      final int amount) {

    final var answer = loanApproval.request(customerId, amount);

    assertThat(answer.getOutcome()).isEqualTo("started");
    return answer.getLoanRequestId();

  }

}
