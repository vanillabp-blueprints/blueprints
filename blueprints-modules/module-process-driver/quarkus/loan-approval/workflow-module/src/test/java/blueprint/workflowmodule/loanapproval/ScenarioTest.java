package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.junit.jupiter.api.extension.ExtendWith;

import blueprint.testdriver.GeneratedClients;
import blueprint.testdriver.loanapproval.ProcessDriver;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import blueprint.workflowmodule.SuppressOutputExtension;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTask;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Base class of the tests of this workflow module. It runs the module with its real adapters,
 * and every system around it is the simulator: the credit rating, the e-signature service and
 * the Business Cockpit. Nothing is mocked.
 *
 * <p>
 * The simulator plays the environment, and the {@link ProcessDriver} plays the users. A test brings
 * a loan approval to a state with the driver. It does not know the steps on the way. Then it acts
 * like the people and systems around the application: through the API of the user interface, with
 * the client generated from {@code loan-approval/openapi/loan-approval.yaml}, and through the test
 * API of the simulator. At the end it checks the workflow aggregate and what the simulated systems
 * were told.
 * </p>
 *
 * <p>
 * All test classes share this configuration, so Quarkus starts the application once for all of
 * them. Each test class says {@code @QuarkusTest} and
 * {@code @WithTestResource(EverySystemIsTheSimulator.class)} itself: Quarkus makes a bean of the
 * class which carries {@code @QuarkusTest}, and a class which only inherits it gets none.
 * {@link EverySystemIsTheSimulator} points the application and the driver at the simulator before
 * it starts.
 * </p>
 */
// The output of a test is shown only if it fails, together with what the simulator logged
// meanwhile. So the suppression comes first. See SimulatorExtension for why the order matters.
@ExtendWith(SuppressOutputExtension.class)
@ExtendWith(SimulatorExtension.class)
public abstract class ScenarioTest extends WorkflowModuleTest {

  /**
   * How the application answers a report of the e-signature service it took, as the callback
   * API says.
   */
  protected static final int REPORT_TAKEN = Response.Status.NO_CONTENT.getStatusCode();

  /** How the application answers a report about a contract it does not know. */
  protected static final int REPORT_OF_AN_UNKNOWN_CONTRACT = Response.Status.NOT_FOUND.getStatusCode();

  /** How the module answers a user who does something it refuses at this point of the process. */
  private static final int REFUSED = Response.Status.CONFLICT.getStatusCode();

  /**
   * How long a test watches that nothing happens. Four times as long as the simulated customers
   * take who act by themselves.
   */
  protected static final Duration A_WHILE = Duration.ofSeconds(1);

  /**
   * Where the application takes the reports of the e-signature service. Quarkus knows the port,
   * which {@link EverySystemIsTheSimulator} chose before the application started.
   */
  @TestHTTPResource("/e-signature/callback")
  protected String callbackUrl;

  @Inject
  protected ProcessDriver driver;

  @Inject
  protected AggregateRepository loanApprovals;

  /**
   * The API of the user interface of the module, as a browser or a form uses it. The process
   * driver brings the client along.
   */
  @Inject
  protected LoanApprovalApi loanApproval;

  /**
   * What the module answers when a user does something it refuses at this point of the process,
   * for example checks a contract the customer has not signed yet. The task stays open, and the
   * user may try again later.
   *
   * @param userAction What the user does.
   * @return The reason the module gives.
   * @throws AssertionError If the module did not refuse.
   */
  protected static String reasonOfTheRefusal(
      final Runnable userAction) {

    try {
      userAction.run();
    } catch (WebApplicationException refused) {
      if (refused
          .getResponse()
          .getStatus() != REFUSED) {
        throw refused;
      }
      return refused
          .getResponse()
          .readEntity(String.class);
    }
    throw new AssertionError("The module did not refuse what the user did");

  }

  /**
   * Watches for {@link #A_WHILE} that something stays true, for example that no signature
   * arrives from a customer who never signs. Each look runs in a transaction of its own, so a
   * condition may read the aggregate, and it sees what the BPMS wrote meanwhile.
   *
   * @param condition What has to stay true.
   */
  protected static void staysSoForAWhile(
      final Callable<Boolean> condition) {

    await()
        .during(A_WHILE)
        .atMost(A_WHILE.multipliedBy(2))
        .until(() -> QuarkusTransaction
            .requiringNew()
            .call(condition));

  }

  /**
   * Waits until the cockpit knows a user task and the condition holds. The cockpit adapter
   * reports through the outbox, so a report arrives a moment after the event which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the task.
   * @return The user task, as the official GUI API of the cockpit returns it.
   */
  protected UserTask awaitUserTask(
      final String userTaskId,
      final Predicate<UserTask> condition) {

    return await("the report of user task '"
        + userTaskId
        + "' in the cockpit")
        .atMost(TIMEOUT)
        .pollDelay(Duration.ZERO)
        .until(() -> userTask(userTaskId), found -> found.filter(condition).isPresent())
        .orElseThrow();

  }

  private Optional<UserTask> userTask(
      final String userTaskId) {

    try {
      // The simulator answers the cockpit below a prefix of its own, and below it the path of
      // the official GUI API, which only the support library of the cockpit adds by itself.
      return Optional.ofNullable(GeneratedClients
          .at(simulator().baseUrl()
              + "/official-api/gui/api/v1")
          .of(OfficialTasklistApi.class)
          .getUserTask(userTaskId, null));
    } catch (WebApplicationException e) {
      // The REST client of Quarkus answers 404 with a WebApplicationException, not with the
      // subclass NotFoundException. So the status decides.
      if (e
          .getResponse()
          .getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
        throw e;
      }
      // The report has not arrived yet.
      return Optional.empty();
    }

  }

}
