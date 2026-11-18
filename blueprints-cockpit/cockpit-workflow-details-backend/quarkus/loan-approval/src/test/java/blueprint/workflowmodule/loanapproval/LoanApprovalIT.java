package blueprint.workflowmodule.loanapproval;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.client.CasesApi;
import blueprint.workflowmodule.loanapproval.client.model.Case;
import blueprint.workflowmodule.loanapproval.model.Aggregate.Status;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

/**
 * The integration test of this workflow module. It starts a real workflow in a real BPMS,
 * reports to the dev shell simulator as the Business Cockpit, and withdraws the loan request
 * through the endpoint the case page calls.
 *
 * <p>
 * It asserts on two things. The workflow aggregate says how far the process came, as in every
 * blueprint. The simulator says what the cockpit was told: which case it shows, which user tasks
 * the case page gets, and what the columns of the case list read. No user interface is involved.
 * The endpoints are called the way the case page calls them, only without the proxy of the
 * cockpit in between.
 * </p>
 *
 * <p>
 * The client of the case page is generated from the OpenAPI file of the endpoints, like the
 * client of the page itself. So the test writes no path and no field of an answer by hand.
 * </p>
 */
@QuarkusTest
@WithTestResource(LoanApprovalIT.ReportToTheSimulator.class)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  /**
   * Where the module answers. The paths of the OpenAPI file are relative to its server, which the
   * cockpit forwards to the path of the module.
   */
  @TestHTTPResource("/loan-approval")
  URI module;

  private CasesApi casePage;

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it.
   */
  public static class ReportToTheSimulator implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {

      return Map.of(
          "vanillabp.cockpit.rest.base-url", CockpitSimulator.start().baseUrl());

    }

    @Override
    public void stop() {

      // The simulator ends with the test run, through its shutdown hook.

    }

  }

  @BeforeEach
  public void createClient() {

    casePage = QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(module)
        .build(CasesApi.class);

  }

  @Test
  public void withdrawingEndsTheCaseNormallyWithTheStatusWithdrawn() {

    final var cockpit = CockpitSimulator.start();
    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "Jane Doe", 5000);

    // The user task is open, and the cockpit shows the case under the business id.
    final var reviewTaskId = awaitReviewTask(loanRequestId);
    final var running = cockpit.awaitWorkflow(loanRequestId, workflow -> true);
    assertThat(running.get("workflowModuleId")).isEqualTo("loan-approval");
    assertThat(running.get("bpmnProcessId")).isEqualTo("loan_approval");
    assertThat(running.get("businessId")).isEqualTo(loanRequestId);
    assertThat(running.get("uiUriType")).isEqualTo("ES_MODULES");
    assertThat(running.get("endedAt")).isNull();
    assertThat(details(running))
        .containsEntry(WorkflowTaskHandler.APPLICANT, "Jane Doe")
        .containsEntry(WorkflowTaskHandler.STATUS, "REQUESTED");

    // What the case page shows: the open user task, from the cockpit ...
    final var workflowId = (String) running.get("id");
    final var userTasks = cockpit.awaitUserTasksOfWorkflow(workflowId, tasks -> !tasks.isEmpty());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().get("id")).isEqualTo(reviewTaskId);
    assertThat(userTasks.getFirst().get("taskDefinition")).isEqualTo("reviewLoanRequest");
    assertThat(userTasks.getFirst().get("endedAt")).isNull();

    // ... and the data of the loan request, from the module.
    assertThat(casePage.getCase(loanRequestId))
        .isEqualTo(new Case(loanRequestId, "Jane Doe", 5000, Case.StatusEnum.REQUESTED));

    // The button "Withdraw loan request".
    casePage.withdrawLoanRequest(loanRequestId);

    // The event subprocess ended the user task, recorded the withdrawal and ended the workflow.
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getStatus() == Status.WITHDRAWN);
    assertThat(loanRequest.getReviewTaskId()).isNull();
    cockpit.awaitUserTasksOfWorkflow(
        workflowId,
        tasks -> tasks.getFirst().get("endedAt") != null);

    // The case list shows the case as ended, and the column says that it was withdrawn. For the
    // cockpit, the case ended normally: withdrawing is a business decision, and the process
    // reached an end event.
    final var withdrawn = cockpit.awaitWorkflow(
        loanRequestId,
        workflow -> workflow.get("endedAt") != null);
    assertThat(details(withdrawn))
        .containsEntry(WorkflowTaskHandler.APPLICANT, "Jane Doe")
        .containsEntry(WorkflowTaskHandler.STATUS, "WITHDRAWN");
    assertThat(withdrawn.get("endReason")).isEqualTo("COMPLETED");

    assertThat(casePage.getCase(loanRequestId).getStatus()).isEqualTo(Case.StatusEnum.WITHDRAWN);

  }

  @Test
  public void anApprovedCaseEndsTheRegularWay() {

    final var cockpit = CockpitSimulator.start();
    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "John Doe", 7000);
    awaitReviewTask(loanRequestId);

    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.DONE);

    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getStatus() == Status.APPROVED);
    final var approved = cockpit.awaitWorkflow(
        loanRequestId,
        workflow -> workflow.get("endedAt") != null);
    assertThat(details(approved)).containsEntry(WorkflowTaskHandler.STATUS, "APPROVED");
    assertThat(approved.get("endReason")).isEqualTo("COMPLETED");

    // An approved loan request cannot be withdrawn any more.
    assertThat(refusalOf(() -> casePage.withdrawLoanRequest(loanRequestId)))
        .isEqualTo(Response.Status.CONFLICT.getStatusCode());
    assertThat(casePage.getCase(loanRequestId).getStatus()).isEqualTo(Case.StatusEnum.APPROVED);

  }

  @Test
  public void aLoanRequestCanBeWithdrawnOnce() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "Jane Doe", 5000);
    awaitReviewTask(loanRequestId);

    casePage.withdrawLoanRequest(loanRequestId);
    assertThat(refusalOf(() -> casePage.withdrawLoanRequest(loanRequestId)))
        .isEqualTo(Response.Status.CONFLICT.getStatusCode());
    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.NOT_POSSIBLE);

  }

  @Test
  public void anUnknownCaseGets404() {

    final var unknown = UUID.randomUUID().toString();

    assertThat(refusalOf(() -> casePage.getCase(unknown)))
        .isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(refusalOf(() -> casePage.withdrawLoanRequest(unknown)))
        .isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

  }

  /**
   * The interface of the endpoints is generated from the OpenAPI the frontend generates its
   * client from. Its paths are relative to the server of the file, and the controller puts the
   * path of the module in front. This checks the result: every operation in the file has its
   * endpoint at the path the cockpit forwards to. Quarkus answers {@code OPTIONS} for a path it
   * has an endpoint for, and names the methods of that path in the header {@code Allow}.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void everyOperationOfTheOpenApiHasAnEndpoint() {

    final Map<String, Object> openApi = new Yaml().load(
        LoanApprovalIT.class.getResourceAsStream("/loan-approval/openapi/loan-approval.yaml"));

    // The browser calls the cockpit below /wm/loan-approval. The cockpit forwards to the
    // workflow module below /loan-approval, the path of its workflow-module-uri.
    final var servers = (List<Map<String, Object>>) openApi.get("servers");
    assertThat(servers.getFirst().get("url")).isEqualTo("/wm/loan-approval");
    final var pathOfTheModule = "/loan-approval";

    final Set<String> offered = new HashSet<>();
    final Set<String> specified = new HashSet<>();
    ((Map<String, Map<String, Object>>) openApi.get("paths")).forEach((
        path,
        operations) -> {
      final var allowed = given()
          .options(pathOfTheModule + path.replace("{loanRequestId}", "any-loan-request"))
          .header(HttpHeaders.ALLOW);
      operations
          .keySet()
          .stream()
          .filter(key -> !"parameters".equals(key))
          .forEach(method -> {
            specified.add(method.toUpperCase()
                + " "
                + pathOfTheModule
                + path);
            if ((allowed != null) && Arrays.asList(allowed.split(",\\s*")).contains(method.toUpperCase())) {
              offered.add(method.toUpperCase()
                  + " "
                  + pathOfTheModule
                  + path);
            }
          });
    });

    assertThat(specified).hasSize(2);
    assertThat(offered).containsAll(specified);

  }

  private String awaitReviewTask(
      final String loanRequestId) {

    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getReviewTaskId() != null)
        .getReviewTaskId();

  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> details(
      final Map<String, Object> workflow) {

    final var details = (Map<String, Object>) workflow.get("details");
    return details == null ? Map.of() : details;

  }

  /**
   * Calls an endpoint which the module has to refuse. The generated client throws for every
   * answer which is not a success.
   *
   * @param call The call of the generated client.
   * @return The status of the refusal.
   */
  private static int refusalOf(
      final Runnable call) {

    try {
      call.run();
    } catch (WebApplicationException refused) {
      return refused
          .getResponse()
          .getStatus();
    }
    throw new AssertionError("The module did not refuse the call");

  }

}
