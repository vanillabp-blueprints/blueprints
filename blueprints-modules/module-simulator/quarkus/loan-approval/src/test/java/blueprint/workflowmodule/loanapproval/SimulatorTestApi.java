package blueprint.workflowmodule.loanapproval;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;

import blueprint.simulator.testapi.client.CreditRatingTestApi;
import blueprint.simulator.testapi.client.ESignatureTestApi;
import blueprint.simulator.testapi.client.model.Envelope;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

/**
 * The client of the simulator's test API, and of the cockpit API the simulator plays.
 *
 * <p>
 * The test API knows nothing about the workflow. It creates data in a simulated system and
 * makes a system act, for example sign a contract. Which of these steps a loan approval needs,
 * and in which order, is known to the test alone. Keep it that way: a test API which knows the
 * process breaks with every change of the model.
 * </p>
 *
 * <p>
 * The client of the test API is generated from
 * {@code simulator-test-api/simulator-test-api.yaml}, the file the simulator implements, and
 * the REST client of Quarkus implements it. The REST client exists only while Quarkus runs, so
 * a client is made for each call and not when this class is made: {@link SimulatorExtension}
 * asks {@link #isRunning()} before Quarkus starts. That is why the status is asked with
 * RestAssured. The official API of the cockpit is read with RestAssured as well, because the
 * cockpit publishes no client of its own.
 * </p>
 *
 * <p>
 * Where the simulator runs comes from the system property {@code simulator.base-url}. The build
 * sets it when it starts the simulator. Without it, the simulator is expected at
 * {@code http://localhost:8079}, where
 * {@code java -jar simulator/target/simulator-1.0.0-SNAPSHOT-runnable.jar} starts it.
 * </p>
 */
public final class SimulatorTestApi {

  /** Where a simulator started by hand answers. */
  private static final String DEFAULT_BASE_URL = "http://localhost:8079";

  /**
   * As long as {@code WorkflowModuleTest.TIMEOUT}. A report follows the engine command it is
   * about, and that command may come one retry of the outbox late.
   */
  private static final Duration TIMEOUT = Duration.ofMinutes(1);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private final String baseUrl;

  private SimulatorTestApi(
      final String baseUrl) {

    this.baseUrl = baseUrl;

  }

  /**
   * The client of the simulator this test run uses.
   *
   * @return The client.
   */
  public static SimulatorTestApi simulator() {

    return new SimulatorTestApi(System.getProperty("simulator.base-url", DEFAULT_BASE_URL));

  }

  /**
   * Where the simulator answers. The adapters of the application are pointed below this.
   *
   * @return The address of the simulator.
   */
  public String baseUrl() {

    return baseUrl;

  }

  /**
   * Whether the simulator answers at all.
   *
   * @return {@code true} if it does.
   */
  public boolean isRunning() {

    try {
      // The path of the generated StatusApi. Its client cannot be used here, see above.
      return "running".equals(given()
          .baseUri(baseUrl)
          .get("/simulator/test-api/status")
          .asString());
    } catch (Exception e) {
      // Nobody answers. RestAssured throws the ConnectException of the JDK for that, but it
      // does not declare it, so it cannot be caught by its own type.
      return false;
    }

  }

  /**
   * Reserves a customer id at the credit rating which no other test uses.
   *
   * @return The customer id.
   */
  public String nextCustomerId() {

    return testApi(CreditRatingTestApi.class).nextId();

  }

  /**
   * Makes the credit rating know a customer.
   *
   * @param customerId The id of the customer.
   * @param score      The score the credit rating answers for this customer.
   */
  public void createRating(
      final String customerId,
      final int score) {

    testApi(CreditRatingTestApi.class).createRating(customerId, score);

  }

  /**
   * The document the e-signature service holds under an id.
   *
   * @param contractId The id the application gave the contract.
   * @return The document, as the e-signature service received it, or empty if there is none.
   */
  public Optional<Envelope> envelope(
      final String contractId) {

    try {
      return Optional.of(testApi(ESignatureTestApi.class).envelope(contractId));
    } catch (WebApplicationException e) {
      if (e
          .getResponse()
          .getStatus() == Status.NOT_FOUND.getStatusCode()) {
        return Optional.empty();
      }
      throw e;
    }

  }

  /**
   * Makes the e-signature service sign a contract and report it to the application.
   *
   * @param contractId The id the application gave the contract.
   * @return What the simulator answered: its status and its text, which names the status the
   *         application answered the callback with.
   */
  public String sign(
      final String contractId) {

    try {
      return Status.OK.getStatusCode()
          + " "
          + testApi(ESignatureTestApi.class).sign(contractId);
    } catch (WebApplicationException e) {
      // 404 and 502 are answers of the simulator as well. Their text says what went wrong.
      return e
          .getResponse()
          .getStatus()
          + " "
          + e
              .getResponse()
              .readEntity(String.class);
    }

  }

  /**
   * Waits until the cockpit knows a user task and the condition holds. The cockpit adapter
   * reports through the outbox, so a report arrives a moment after the event which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the task.
   * @return The user task, as the official API of the cockpit returns it.
   */
  public Map<String, Object> awaitUserTask(
      final String userTaskId,
      final Predicate<Map<String, Object>> condition) {

    final var lastSeen = new AtomicReference<Optional<Map<String, Object>>>(Optional.empty());

    try {

      await()
          .atMost(TIMEOUT)
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            final var found = userTask(userTaskId);
            lastSeen.set(found);
            return found
                .filter(condition)
                .isPresent();
          });

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The cockpit did not get the expected report of user task '"
              + userTaskId
              + "' within "
              + TIMEOUT
              + ". Last seen: "
              + lastSeen
                  .get()
                  .map(Object::toString)
                  .orElse("nothing - the task was never reported"), e);

    }

    return lastSeen
        .get()
        .orElseThrow();

  }

  private Optional<Map<String, Object>> userTask(
      final String userTaskId) {

    final var response = cockpit()
        .pathParam("userTaskId", userTaskId)
        .get("/usertask/{userTaskId}");

    // The simulator answers a task it does not know with an error. That only means the report
    // has not arrived yet.
    return response.statusCode() == 200
        ? Optional.ofNullable(asMap(response))
        : Optional.empty();

  }

  /**
   * Reads a JSON object into plain Java maps and lists. {@code response.as(Map.class)} would ask
   * RestAssured's Jackson, and that registers every Jackson module on the classpath. The Operaton
   * engine brings the Scala module, which turns a nested JSON object into a Scala map. Such a map
   * equals no Java map, so an assertion on the details of a task fails although the values are
   * the same. JsonPath parses with Groovy and returns Java maps on every BPMS. The answers of
   * the test API are read into generated classes, so only the cockpit's answers need this.
   */
  private static Map<String, Object> asMap(
      final Response response) {

    return response
        .jsonPath()
        .getMap("$");

  }

  private <T> T testApi(
      final Class<T> api) {

    return QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create(baseUrl))
        .build(api);

  }

  private RequestSpecification cockpit() {

    return given()
        .baseUri(baseUrl)
        .basePath("/official-api/v1");

  }

}
