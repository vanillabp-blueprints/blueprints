package blueprint.workflowmodule.loanapproval;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;

import blueprint.simulator.testapi.client.CreditRatingTestApi;
import blueprint.simulator.testapi.client.ESignatureTestApi;
import blueprint.simulator.testapi.client.StatusApi;
import blueprint.simulator.testapi.client.config.SimulatorTestApiClientAutoConfiguration;
import blueprint.simulator.testapi.client.model.Envelope;
import feign.FeignException;
import feign.RetryableException;
import io.vanillabp.cockpit.gui.api.v1.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.UserTask;

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
 * {@code simulator-test-api/simulator-test-api.yaml}, the file the simulator implements. Its
 * module brings an AutoConfiguration, so this class gets the clients the way an application
 * does: it starts a small Spring context with that AutoConfiguration and sets one property. A
 * JUnit extension uses this class before the context of the test starts, so it cannot take the
 * clients from there.
 * </p>
 *
 * <p>
 * The official API of the cockpit is read with a {@link RestClient}, because the cockpit
 * publishes no Java client of it. The path and the model come from the classes the cockpit
 * generates from that API, {@link OfficialTasklistApi} and {@link UserTask}.
 * </p>
 *
 * <p>
 * Where the simulator runs comes from the system property {@code simulator.base-url}. The build
 * sets it when it starts the simulator. Without it, the simulator is expected at
 * {@code http://localhost:8079}, where {@code mvn -pl simulator spring-boot:run} starts it.
 * </p>
 */
public final class SimulatorTestApi {

  /** Where a simulator started by hand answers. */
  private static final String DEFAULT_BASE_URL = "http://localhost:8079";

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  /** One client per address, because each holds a small Spring context. */
  private static final Map<String, SimulatorTestApi> CLIENTS = new ConcurrentHashMap<>();

  private final String baseUrl;

  private final StatusApi status;

  private final CreditRatingTestApi creditRating;

  private final ESignatureTestApi eSignature;

  private final RestClient cockpit;

  private SimulatorTestApi(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    final var clients = new AnnotationConfigApplicationContext();
    clients
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("simulator", Map.of("simulator-test-api.base-url", baseUrl)));
    clients.register(JacksonAutoConfiguration.class, SimulatorTestApiClientAutoConfiguration.class);
    clients.refresh();
    clients.registerShutdownHook();
    this.status = clients.getBean(StatusApi.class);
    this.creditRating = clients.getBean(CreditRatingTestApi.class);
    this.eSignature = clients.getBean(ESignatureTestApi.class);
    this.cockpit = RestClient.create(baseUrl
        + "/official-api/v1");

  }

  /**
   * The client of the simulator this test run uses.
   *
   * @return The client.
   */
  public static SimulatorTestApi simulator() {

    return CLIENTS.computeIfAbsent(baseUrlOfThisRun(), SimulatorTestApi::new);

  }

  /**
   * Where the simulator of this test run answers, without building a client.
   *
   * @return The address of the simulator.
   */
  public static String baseUrlOfThisRun() {

    return System.getProperty("simulator.base-url", DEFAULT_BASE_URL);

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
      return "running".equals(status.status());
    } catch (RetryableException e) {
      // Nothing answers at the address.
      return false;
    }

  }

  /**
   * Reserves a customer id at the credit rating which no other test uses.
   *
   * @return The customer id.
   */
  public String nextCustomerId() {

    return creditRating.nextId();

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

    creditRating.createRating(customerId, score);

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
      return Optional.of(eSignature.envelope(contractId));
    } catch (FeignException.NotFound e) {
      return Optional.empty();
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
      return "200 "
          + eSignature.sign(contractId);
    } catch (RetryableException e) {
      throw e;
    } catch (FeignException e) {
      // 404 and 502 are answers of the simulator as well. Their text says what went wrong.
      return e.status()
          + " "
          + e.contentUTF8();
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
  public UserTask awaitUserTask(
      final String userTaskId,
      final Predicate<UserTask> condition) {

    final var lastSeen = new AtomicReference<Optional<UserTask>>(Optional.empty());

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

  private Optional<UserTask> userTask(
      final String userTaskId) {

    return cockpit
        .get()
        .uri(OfficialTasklistApi.PATH_GET_USER_TASK, userTaskId)
        // The simulator answers a task it does not know with an error. That only means the
        // report has not arrived yet.
        .exchange((
            request,
            response) -> response.getStatusCode().is2xxSuccessful()
                ? Optional.ofNullable(response.bodyTo(UserTask.class))
                : Optional.empty());

  }

}
