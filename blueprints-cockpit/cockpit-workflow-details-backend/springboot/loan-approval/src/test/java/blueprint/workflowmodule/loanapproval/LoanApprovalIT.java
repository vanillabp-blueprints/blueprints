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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.client.CasesApi;
import blueprint.workflowmodule.loanapproval.client.model.Case;
import blueprint.workflowmodule.loanapproval.model.Aggregate.Status;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialWorkflowlistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.EndReason;
import io.vanillabp.cockpit.gui.api.v1.client.model.Workflow;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service loanApproval;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping endpoints;

  /** The case list of the cockpit, read from the simulator. */
  @Autowired
  private OfficialWorkflowlistApi workflowlist;

  @Value("${local.server.port}")
  private int port;

  private CasesApi casePage;

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it. The
   * client of the official GUI API, which the test reads what the cockpit knows with, gets the
   * address of the simulated cockpit as well. It is the client of the library, with the keys
   * where Spring Boot looks for them.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void reportToTheSimulator(
      final DynamicPropertyRegistry properties) {

    properties.add("vanillabp.cockpit.rest.base-url", () -> CockpitSimulator.start().baseUrl());
    properties.add("spring.http.serviceclient.official-gui-api.base-url",
        () -> CockpitSimulator.start().cockpitBaseUrl());

  }

  @BeforeEach
  public void createClient() {

    // The paths of the OpenAPI file are relative to its server, which the cockpit forwards to
    // the path of the module.
    casePage = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(RestClient.create("http://localhost:"
            + port
            + "/loan-approval")))
        .build()
        .createClient(CasesApi.class);

  }

  @Test
  public void withdrawingEndsTheCaseNormallyWithTheStatusWithdrawn() {

    final var cockpit = CockpitSimulator.start();
    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "Jane Doe", 5000);

    // The user task is open, and the cockpit shows the case under the business id.
    final var reviewTaskId = awaitReviewTask(loanRequestId);
    final var running = cockpit.awaitWorkflow(workflowlist, loanRequestId, workflow -> true);
    assertThat(running.getWorkflowModuleId()).isEqualTo("loan-approval");
    assertThat(running.getBpmnProcessId()).isEqualTo("loan_approval");
    assertThat(running.getBusinessId()).isEqualTo(loanRequestId);
    assertThat(running.getUiUriType()).isEqualTo("ES_MODULES");
    assertThat(running.getEndedAt()).isNull();
    assertThat(details(running))
        .containsEntry(WorkflowTaskHandler.APPLICANT, "Jane Doe")
        .containsEntry(WorkflowTaskHandler.STATUS, "REQUESTED");

    // What the case page shows: the open user task, from the cockpit ...
    final var workflowId = running.getId();
    final var userTasks = cockpit.awaitUserTasksOfWorkflow(workflowlist, workflowId, tasks -> !tasks.isEmpty());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getId()).isEqualTo(reviewTaskId);
    assertThat(userTasks.getFirst().getTaskDefinition()).isEqualTo("reviewLoanRequest");
    assertThat(userTasks.getFirst().getEndedAt()).isNull();

    // ... and the data of the loan request, from the module.
    assertThat(casePage.getCase(loanRequestId))
        .isEqualTo(new Case(loanRequestId, "Jane Doe", 5000, Case.StatusEnum.REQUESTED));

    // The button "Withdraw loan request".
    casePage.withdrawLoanRequest(loanRequestId);

    // The event subprocess ended the user task, recorded the withdrawal and ended the workflow.
    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getStatus() == Status.WITHDRAWN);
    assertThat(loanRequest.getReviewTaskId()).isNull();
    cockpit.awaitUserTasksOfWorkflow(
        workflowlist,
        workflowId,
        tasks -> tasks.getFirst().getEndedAt() != null);

    // The case list shows the case as ended, and the column says that it was withdrawn. For the
    // cockpit, the case ended normally: withdrawing is a business decision, and the process
    // reached an end event.
    final var withdrawn = cockpit.awaitWorkflow(
        workflowlist,
        loanRequestId,
        workflow -> workflow.getEndedAt() != null);
    assertThat(details(withdrawn))
        .containsEntry(WorkflowTaskHandler.APPLICANT, "Jane Doe")
        .containsEntry(WorkflowTaskHandler.STATUS, "WITHDRAWN");
    assertThat(withdrawn.getEndReason()).isEqualTo(EndReason.COMPLETED);

    assertThat(casePage.getCase(loanRequestId).status()).isEqualTo(Case.StatusEnum.WITHDRAWN);

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
        workflowlist,
        loanRequestId,
        workflow -> workflow.getEndedAt() != null);
    assertThat(details(approved)).containsEntry(WorkflowTaskHandler.STATUS, "APPROVED");
    assertThat(approved.getEndReason()).isEqualTo(EndReason.COMPLETED);

    // An approved loan request cannot be withdrawn any more.
    assertThat(refusalOf(() -> casePage.withdrawLoanRequest(loanRequestId)))
        .isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(casePage.getCase(loanRequestId).status()).isEqualTo(Case.StatusEnum.APPROVED);

  }

  @Test
  public void aLoanRequestCanBeWithdrawnOnce() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, "Jane Doe", 5000);
    awaitReviewTask(loanRequestId);

    casePage.withdrawLoanRequest(loanRequestId);
    assertThat(refusalOf(() -> casePage.withdrawLoanRequest(loanRequestId)))
        .isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.NOT_POSSIBLE);

  }

  @Test
  public void anUnknownCaseGets404() {

    final var unknown = UUID.randomUUID().toString();

    assertThat(refusalOf(() -> casePage.getCase(unknown))).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(refusalOf(() -> casePage.withdrawLoanRequest(unknown)))
        .isEqualTo(HttpStatus.NOT_FOUND.value());

  }

  /**
   * The interface of the endpoints is generated from the OpenAPI the frontend generates its
   * client from. Its paths are relative to the server of the file, and the controller puts the
   * path of the module in front. This checks the result: every operation in the file has its
   * endpoint at the path the cockpit forwards to.
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

  private static Map<String, Object> details(
      final Workflow workflow) {

    return workflow.getDetails() == null ? Map.of() : workflow.getDetails();

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
    } catch (HttpStatusCodeException refused) {
      return refused
          .getStatusCode()
          .value();
    }
    throw new AssertionError("The module did not refuse the call");

  }

}
