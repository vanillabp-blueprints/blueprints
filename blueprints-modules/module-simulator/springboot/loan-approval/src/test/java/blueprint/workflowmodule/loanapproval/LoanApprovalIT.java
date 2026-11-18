package blueprint.workflowmodule.loanapproval;

import static blueprint.workflowmodule.loanapproval.SimulatorTestApi.simulator;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.workflowmodule.SuppressOutputExtension;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.client.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
// The output of a test is shown only if it fails, together with what the simulator logged
// meanwhile. So the suppression comes first. See SimulatorExtension for why the order matters.
@ExtendWith(SuppressOutputExtension.class)
@ExtendWith(SimulatorExtension.class)
public class LoanApprovalIT extends WorkflowModuleTest {

  /**
   * The port of the application under test. It is chosen before the application starts, because
   * the e-signature service has to know where to call back.
   */
  private static final int PORT = freePort();

  /** Where the application takes the reports of the e-signature service. */
  private static final String CALLBACK_URL = "http://localhost:"
      + PORT
      + "/e-signature/callback";

  /**
   * How the application answers a report of the e-signature service it took, as the callback
   * API says.
   */
  private static final int REPORT_TAKEN = HttpStatus.NO_CONTENT.value();

  /** How the application answers a report about a contract it does not know. */
  private static final int REPORT_OF_AN_UNKNOWN_CONTRACT = HttpStatus.NOT_FOUND.value();

  @Autowired
  private AggregateRepository loanApprovals;

  /** The task list of the cockpit the simulator plays. */
  @Autowired
  private OfficialTasklistApi tasklist;

  /**
   * The API of the module, through the client the build generates from its OpenAPI file. The
   * paths in the file are complete, so the client only needs the address of the application.
   */
  private final LoanApprovalApi loanApproval = HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(RestClient.create("http://localhost:"
          + PORT)))
      .build()
      .createClient(LoanApprovalApi.class);

  /**
   * Points every adapter at the simulator, before the application starts. The application does
   * the same in its profile 'local', with the port a simulator started by hand uses.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void everySystemIsTheSimulator(
      final DynamicPropertyRegistry properties) {

    final var simulator = simulator().baseUrl();
    properties.add("server.port", () -> PORT);
    properties.add("credit-rating.client.base-url", () -> simulator
        + "/credit-rating");
    properties.add("e-signature.client.base-url", () -> simulator
        + "/e-signature");
    properties.add("e-signature.callback-url", () -> CALLBACK_URL);
    properties.add("vanillabp.cockpit.rest.base-url", () -> simulator);
    // The client of the cockpit's library, with the keys where Spring Boot looks for them. The
    // simulator answers the cockpit below a prefix of its own, and the client adds /gui/api/v1.
    properties.add("spring.http.serviceclient.official-gui-api.base-url", () -> simulator
        + "/official-api");

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
        loanApprovals,
        loanRequestId,
        loanRequest -> loanRequest.getPrepareContractTaskId() != null)
        .getPrepareContractTaskId();
    assertThat(awaitAggregate(loanApprovals, loanRequestId).getCreditScore()).isEqualTo(720);

    // The cockpit shows the user task, with the details the module reported.
    final var userTask = simulator().awaitUserTask(tasklist, prepareContractTaskId, task -> true);
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
        loanApprovals,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSentForSigning()));
    assertThat(sent.getInterestRate()).isEqualByComparingTo(new BigDecimal("3.5"));
    assertThat(simulator().envelope(loanRequestId))
        .hasValueSatisfying(envelope -> {
          assertThat(envelope.signer()).isEqualTo(customerId);
          assertThat(envelope.document()).contains("5000", "3.5");
        });

    // The customer signs. The simulator calls the application back, and the workflow ends.
    assertThat(simulator().sign(loanRequestId).answer()).as("the application took the report")
        .isEqualTo(REPORT_TAKEN);
    assertThat(awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned())))
        .isNotNull();

    // The cockpit took the user task off the list.
    simulator().awaitUserTask(tasklist, prepareContractTaskId, task -> task.getEndedAt() != null);

  }

  @Test
  public void aCustomerWithALowCreditScoreIsDeclinedAtOnce() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 480);

    final var answer = loanApproval.request(customerId, 5000);

    assertThat(answer.outcome()).isEqualTo("declined");
    assertThat(loanApprovals.findById(answer.loanRequestId())).isEmpty();

  }

  @Test
  public void aCustomerTheCreditRatingDoesNotKnowIsDeclinedAtOnce() {

    // A reserved id the credit rating was never told about.
    final var customerId = simulator().nextCustomerId();

    final var answer = loanApproval.request(customerId, 5000);

    assertThat(answer.outcome()).isEqualTo("declined");
    assertThat(loanApprovals.findById(answer.loanRequestId())).isEmpty();

  }

  @Test
  public void aSignatureIsTakenOnceEvenIfItArrivesTwice() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 650);
    final var loanRequestId = request(customerId, 1000);
    final var taskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanRequest -> loanRequest.getPrepareContractTaskId() != null)
        .getPrepareContractTaskId();
    loanApproval.prepareContract(loanRequestId, taskId, new BigDecimal("4"));
    awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSentForSigning()));

    assertThat(simulator().sign(loanRequestId).answer()).as("the application took the report")
        .isEqualTo(REPORT_TAKEN);
    awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()));

    // A service may report again, for example after it lost the first answer.
    assertThat(simulator().sign(loanRequestId).answer()).as("the application took the report again")
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

    final var report = simulator().sendCallback(unknownContract, CALLBACK_URL);

    assertThat(report.answer()).as("the application does not know the contract")
        .isEqualTo(REPORT_OF_AN_UNKNOWN_CONTRACT);
    assertThat(loanApprovals.findById(unknownContract)).isEmpty();

  }

  private String request(
      final String customerId,
      final int amount) {

    final var answer = loanApproval.request(customerId, amount);

    assertThat(answer.outcome()).isEqualTo("started");
    return answer.loanRequestId();

  }

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port for the application under test", e);
    }

  }

}
