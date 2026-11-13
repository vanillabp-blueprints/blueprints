package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate.Status;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service loanApproval;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping endpoints;

  @Value("${local.server.port}")
  private int port;

  private RestClient casePage;

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void reportToTheSimulator(
      final DynamicPropertyRegistry properties) {

    properties.add("vanillabp.cockpit.rest.base-url", () -> CockpitSimulator.start().baseUrl());

  }

  @BeforeEach
  public void createClient() {

    casePage = RestClient.create("http://localhost:"
        + port
        + "/loan-approval/api/cases");

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
    assertThat(read(loanRequestId))
        .containsEntry("loanRequestId", loanRequestId)
        .containsEntry("applicant", "Jane Doe")
        .containsEntry("amount", 5000)
        .containsEntry("status", "REQUESTED");

    // The button "Withdraw loan request".
    assertThat(withdraw(loanRequestId)).isEqualTo(204);

    // The event subprocess ended the user task, recorded the withdrawal and ended the workflow.
    final var loanRequest = awaitAggregate(
        loanApprovals,
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

    assertThat(read(loanRequestId)).containsEntry("status", "WITHDRAWN");

  }

  @Test
  public void anApprovedCaseEndsTheRegularWay() {

    final var cockpit = CockpitSimulator.start();
    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "John Doe", 7000);
    awaitReviewTask(loanRequestId);

    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.DONE);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getStatus() == Status.APPROVED);
    final var approved = cockpit.awaitWorkflow(
        loanRequestId,
        workflow -> workflow.get("endedAt") != null);
    assertThat(details(approved)).containsEntry(WorkflowTaskHandler.STATUS, "APPROVED");
    assertThat(approved.get("endReason")).isEqualTo("COMPLETED");

    // An approved loan request cannot be withdrawn any more.
    assertThat(withdraw(loanRequestId)).isEqualTo(409);
    assertThat(read(loanRequestId)).containsEntry("status", "APPROVED");

  }

  @Test
  public void aLoanRequestCanBeWithdrawnOnce() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "Jane Doe", 5000);
    awaitReviewTask(loanRequestId);

    assertThat(withdraw(loanRequestId)).isEqualTo(204);
    assertThat(withdraw(loanRequestId)).isEqualTo(409);
    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.NOT_POSSIBLE);

  }

  @Test
  public void anUnknownCaseGets404() {

    final var unknown = UUID.randomUUID().toString();

    assertThat(status(unknown)).isEqualTo(404);
    assertThat(withdraw(unknown)).isEqualTo(404);

  }

  /**
   * The endpoints are written by hand. This keeps them in line with the OpenAPI the frontend
   * generates its client from: every operation in the file has its endpoint here.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void everyOperationOfTheOpenApiHasAnEndpoint() {

    final Map<String, Object> openApi = new Yaml().load(
        LoanApprovalIT.class.getResourceAsStream("/openapi/loan-approval.yaml"));

    // The browser calls the cockpit below /wm/loan-approval. The cockpit forwards to the
    // workflow module below /loan-approval, the path of its workflow-module-uri.
    final var servers = (List<Map<String, Object>>) openApi.get("servers");
    assertThat(servers.getFirst().get("url")).isEqualTo("/wm/loan-approval");
    final var pathOfTheModule = "/loan-approval";

    final Set<String> offered = new HashSet<>();
    endpoints
        .getHandlerMethods()
        .keySet()
        .forEach(mapping -> mapping
            .getMethodsCondition()
            .getMethods()
            .forEach(method -> mapping
                .getPatternValues()
                .forEach(pattern -> offered.add(method
                    + " "
                    + pattern))));

    final Set<String> specified = new HashSet<>();
    ((Map<String, Map<String, Object>>) openApi.get("paths")).forEach((
        path,
        operations) -> operations
            .keySet()
            .stream()
            .filter(key -> !"parameters".equals(key))
            .forEach(method -> specified.add(method.toUpperCase()
                + " "
                + pathOfTheModule
                + path)));

    assertThat(specified).hasSize(2);
    assertThat(offered).containsAll(specified);

  }

  private String awaitReviewTask(
      final String loanRequestId) {

    return awaitAggregate(
        loanApprovals,
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> read(
      final String loanRequestId) {

    return casePage
        .get()
        .uri("/{loanRequestId}", loanRequestId)
        .retrieve()
        .body(Map.class);

  }

  private int status(
      final String loanRequestId) {

    return casePage
        .get()
        .uri("/{loanRequestId}", loanRequestId)
        .exchange((
            request,
            response) -> response.getStatusCode().value());

  }

  private int withdraw(
      final String loanRequestId) {

    return casePage
        .post()
        .uri("/{loanRequestId}/withdraw", loanRequestId)
        .exchange((
            request,
            response) -> response.getStatusCode().value());

  }

}
