package blueprint.workflowmodule.loanapproval;

import static blueprint.workflowmodule.loanapproval.SimulatorTestApi.simulator;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

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
@ExtendWith(SimulatorExtension.class)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  AggregateRepository loanApprovals;

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
    assertThat(awaitAggregate(loanApprovals::findByIdOptional, loanRequestId).getCreditScore()).isEqualTo(720);

    // The cockpit shows the user task, with the details the module reported.
    final var userTask = simulator().awaitUserTask(prepareContractTaskId, task -> true);
    assertThat(userTask.get("workflowModuleId")).isEqualTo("loan-approval");
    assertThat(userTask.get("taskDefinition")).isEqualTo("prepareContract");
    assertThat(userTask.get("details"))
        .isEqualTo(Map.of(
            WorkflowTaskHandler.AMOUNT, 5000,
            WorkflowTaskHandler.CREDIT_SCORE, 720));

    // Somebody prepares the contract, and the module hands it to the e-signature service.
    assertThat(api()
        .get("/{loanRequestId}/prepare-contract/{taskId}?interestRate=3.5", loanRequestId, prepareContractTaskId)
        .asString()).contains("prepared");
    final var sent = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSentForSigning()));
    assertThat(sent.getInterestRate()).isEqualByComparingTo(new BigDecimal("3.5"));
    assertThat(simulator().envelope(loanRequestId))
        .hasValueSatisfying(envelope -> {
          assertThat(envelope.get("signer")).isEqualTo(customerId);
          assertThat((String) envelope.get("document")).contains("5000", "3.5");
        });

    // The customer signs. The simulator calls the application back, and the workflow ends.
    assertThat(simulator().sign(loanRequestId)).startsWith("200").contains("204");
    final var signed = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()));
    assertThat(signed.getContractSigned()).isTrue();

    // The cockpit took the user task off the list.
    simulator().awaitUserTask(prepareContractTaskId, task -> task.get("endedAt") != null);

  }

  @Test
  public void aCustomerWithALowCreditScoreIsDeclinedAtOnce() {

    final var customerId = simulator().nextCustomerId();
    simulator().createRating(customerId, 480);

    final var answer = api()
        .get("/request?customerId={customerId}&amount=5000", customerId)
        .as(ApiController.Answer.class);

    assertThat(answer.outcome()).isEqualTo("declined");
    assertThat(loanApprovals.findByIdOptional(answer.loanRequestId())).isEmpty();

  }

  @Test
  public void aCustomerTheCreditRatingDoesNotKnowIsDeclinedAtOnce() {

    // A reserved id the credit rating was never told about.
    final var customerId = simulator().nextCustomerId();

    final var answer = api()
        .get("/request?customerId={customerId}&amount=5000", customerId)
        .as(ApiController.Answer.class);

    assertThat(answer.outcome()).isEqualTo("declined");
    assertThat(loanApprovals.findByIdOptional(answer.loanRequestId())).isEmpty();

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
    api()
        .get("/{loanRequestId}/prepare-contract/{taskId}?interestRate=4", loanRequestId, taskId)
        .then()
        .statusCode(200);
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSentForSigning()));

    assertThat(simulator().sign(loanRequestId)).contains("204");
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()));

    // A service may report again, for example after it lost the first answer.
    assertThat(simulator().sign(loanRequestId)).contains("204");

  }

  @Test
  public void aSignatureOfAnUnknownContractIsRefused() {

    final var status = given()
        .contentType(ContentType.JSON)
        .body(Map.of("contractId", UUID.randomUUID().toString(), "status", "SIGNED"))
        .post("/e-signature/callback")
        .statusCode();

    assertThat(status).isEqualTo(404);

  }

  private String request(
      final String customerId,
      final int amount) {

    final var answer = api()
        .get("/request?customerId={customerId}&amount={amount}", customerId, amount)
        .as(ApiController.Answer.class);

    assertThat(answer.outcome()).isEqualTo("started");
    return answer.loanRequestId();

  }

  /**
   * The API of the module. Quarkus points RestAssured at the application under test.
   *
   * @return A request to the API.
   */
  private static RequestSpecification api() {

    return given().basePath("/api/loan-approval");

  }

}
