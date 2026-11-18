package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import blueprint.workflowmodule.loanapproval.client.StartFormApi;
import blueprint.workflowmodule.loanapproval.client.model.LoanRequest;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalModuleDetails;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.cockpit.workflowmodules.WorkflowModuleDetailsProvider;

/**
 * The integration test of this workflow module. It starts a loan approval through the endpoint
 * the start form calls, runs it in a real BPMS, and reports to the dev shell simulator as the
 * Business Cockpit.
 *
 * <p>
 * It asserts on two things. The workflow aggregate says how far the process came, as in every
 * blueprint. The simulator says what the cockpit was told: the new case is in the case list,
 * with its open user task. No user interface is involved. The endpoint is called the way the
 * start form calls it, only without the proxy of the cockpit in between.
 * </p>
 *
 * <p>
 * The client of the start form is generated from the OpenAPI file of the endpoint, like the
 * client of the form itself. So the test writes no path and no field of the request by hand.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service loanApproval;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private List<WorkflowModuleDetailsProvider> workflowModuleDetails;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping endpoints;

  @Value("${local.server.port}")
  private int port;

  private StartFormApi startForm;

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

    // The paths of the OpenAPI file are relative to its server, which the cockpit forwards to
    // the path of the module.
    startForm = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(RestClient.create("http://localhost:"
            + port
            + "/loan-approval")))
        .build()
        .createClient(StartFormApi.class);

  }

  @Test
  public void theStartFormStartsALoanApprovalAndTheCaseIsInTheCaseList() {

    final var cockpit = CockpitSimulator.start();

    // The button "OK" of the start form.
    final var loanRequestId = startForm
        .requestLoan(new LoanRequest("Jane Doe", 5000))
        .loanRequestId();
    assertThat(loanRequestId).isNotBlank();

    // The module started the process, and the process waits in the user task.
    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getReviewTaskId() != null);
    assertThat(loanRequest.getApplicant()).isEqualTo("Jane Doe");
    assertThat(loanRequest.getAmount()).isEqualTo(5000);

    // The cockpit was not asked to start anything. It learned about the new case from the
    // cockpit adapter, and lists it under the id of the loan request.
    final var newCase = cockpit.awaitWorkflow(loanRequestId, workflow -> true);
    assertThat(newCase.get("workflowModuleId")).isEqualTo("loan-approval");
    assertThat(newCase.get("bpmnProcessId")).isEqualTo("loan_approval");
    assertThat(newCase.get("businessId")).isEqualTo(loanRequestId);
    assertThat(newCase.get("endedAt")).isNull();

    // From here on, the case goes on like every other one: with its user task.
    final var userTasks = cockpit.awaitUserTasksOfWorkflow(
        (String) newCase.get("id"),
        tasks -> !tasks.isEmpty());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().get("id")).isEqualTo(loanRequest.getReviewTaskId());
    assertThat(userTasks.getFirst().get("taskDefinition")).isEqualTo("reviewLoanRequest");

  }

  @Test
  public void aStartFormWithoutAnApplicantOrAPositiveAmountIsRefused() {

    final var before = loanApprovals.count();

    assertThat(refusalOf(new LoanRequest(" ", 5000))).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(refusalOf(new LoanRequest("Jane Doe", 0))).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(refusalOf(new LoanRequest("Jane Doe", null))).isEqualTo(HttpStatus.BAD_REQUEST.value());

    assertThat(loanApprovals.count()).isEqualTo(before);

  }

  /**
   * The cockpit lists this workflow module only for the members of the group the module names.
   * A cockpit user interface loads the button in the header only from the modules in that list.
   * The cockpit applies the groups. The module only has to name them, and this checks that it
   * does.
   */
  @Test
  public void onlyLoanOfficersSeeTheModuleAndSoTheButton() {

    assertThat(workflowModuleDetails).hasSize(1);
    final var details = workflowModuleDetails.getFirst();
    assertThat(details.getWorkflowModuleId()).isEqualTo("loan-approval");
    assertThat(details.getAccessibleToGroups()).containsExactly(LoanApprovalModuleDetails.LOAN_OFFICERS);

  }

  @Test
  public void anApprovedLoanRequestEndsTheCase() {

    final var cockpit = CockpitSimulator.start();

    final var loanRequestId = loanApproval.request("John Doe", 7000);
    awaitAggregate(loanApprovals, loanRequestId, aggregate -> aggregate.getReviewTaskId() != null);

    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.DONE);
    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.NOT_POSSIBLE);

    final var ended = cockpit.awaitWorkflow(loanRequestId, workflow -> workflow.get("endedAt") != null);
    assertThat(ended.get("endReason")).isEqualTo("COMPLETED");

  }

  /**
   * The interface of the endpoint is generated from the OpenAPI the frontend generates its
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

    assertThat(specified).hasSize(1);
    assertThat(offered).containsAll(specified);

  }

  /**
   * Sends a start form which the module has to refuse. The generated client throws for every
   * answer which is not a success.
   *
   * @param loanRequest What the user typed.
   * @return The status of the refusal.
   */
  private int refusalOf(
      final LoanRequest loanRequest) {

    try {
      startForm.requestLoan(loanRequest);
    } catch (HttpStatusCodeException refused) {
      return refused
          .getStatusCode()
          .value();
    }
    throw new AssertionError("The module started a loan approval for "
        + loanRequest);

  }

}
