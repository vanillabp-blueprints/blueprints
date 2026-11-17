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
import org.springframework.http.HttpHeaders;
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
import blueprint.workflowmodule.loanapproval.client.UserTasksApi;
import blueprint.workflowmodule.loanapproval.client.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.client.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.client.model.CheckDocumentsForm;
import blueprint.workflowmodule.loanapproval.client.model.CheckDocumentsInput;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.cockpit.support.usertask.BcTaskClaimedByAnotherUserException;
import io.vanillabp.cockpit.support.usertask.BcTaskNotOpenException;

/**
 * The integration test of this workflow module. It starts a real workflow in a real BPMS,
 * reports to the dev shell simulator as the Business Cockpit, and completes both user tasks
 * through the endpoints the forms call.
 *
 * <p>
 * It asserts on two things. The workflow aggregate says how far the process came, as in every
 * blueprint. The simulator says what the cockpit was told: which user tasks it shows, and what
 * the column of the task list reads. No user interface is involved. The endpoints are called
 * the way a form calls them, only without the proxy of the cockpit in between.
 * </p>
 *
 * <p>
 * The client of the forms is generated from the OpenAPI file of the endpoints, like the client
 * of the forms themselves. So the test writes no path and no field of a form by hand.
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

  /** The forms, sent without a login, like a person who follows a URL of the log. */
  private UserTasksApi forms;

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void reportToTheSimulator(
      final DynamicPropertyRegistry properties) {

    properties.add("vanillabp.cockpit.rest.base-url", () -> CockpitSimulator.start().baseUrl());
    properties.add(Cockpit.ADDRESS, () -> CockpitGuiApi.start().address());

  }

  @BeforeEach
  public void createClient() {

    forms = formsOf(null);

  }

  @Test
  public void bothUserTasksAreShownInTheCockpitAndCompletedByTheirForms() {

    final var cockpit = CockpitSimulator.start();
    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    // The first user task is open, and the cockpit shows it under the same id.
    final var assessRiskTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();
    final var assessRisk = cockpit.awaitUserTask(assessRiskTaskId, task -> true);
    assertThat(assessRisk.get("workflowModuleId")).isEqualTo("loan-approval");
    assertThat(assessRisk.get("taskDefinition")).isEqualTo("assessRisk");
    assertThat(assessRisk.get("uiUriType")).isEqualTo("ES_MODULES");
    assertThat(details(assessRisk)).doesNotContainKey(WorkflowTaskHandler.RISK_ASSESSMENT);

    // The form reads the loan request and completes the task.
    assertThat(forms.getAssessRiskForm(loanRequestId, assessRiskTaskId))
        .isEqualTo(new AssessRiskForm(loanRequestId, 5000, null));
    forms.completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput("acceptable"));

    // The second user task is open. Its report carries the risk assessment for the column of
    // the task list.
    final var checkDocumentsTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCheckDocumentsTaskId() != null)
        .getCheckDocumentsTaskId();
    final var checkDocuments = cockpit.awaitUserTask(
        checkDocumentsTaskId,
        task -> details(task).containsKey(WorkflowTaskHandler.RISK_ASSESSMENT));
    assertThat(checkDocuments.get("taskDefinition")).isEqualTo("checkDocuments");
    assertThat(details(checkDocuments))
        .containsEntry(WorkflowTaskHandler.RISK_ASSESSMENT, "acceptable");

    assertThat(forms.getCheckDocumentsForm(loanRequestId, checkDocumentsTaskId))
        .isEqualTo(new CheckDocumentsForm(loanRequestId, "acceptable", null));
    forms.completeCheckDocuments(loanRequestId, checkDocumentsTaskId, new CheckDocumentsInput("complete"));

    // The workflow ended, and the cockpit took both tasks off the list.
    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> "complete".equals(aggregate.getDocumentsNote()));
    assertThat(loanRequest.getRiskAssessment()).isEqualTo("acceptable");
    assertThat(loanRequest.getAssessRiskTaskId()).isNull();
    assertThat(loanRequest.getCheckDocumentsTaskId()).isNull();
    cockpit.awaitUserTask(assessRiskTaskId, task -> task.get("endedAt") != null);
    cockpit.awaitUserTask(checkDocumentsTaskId, task -> task.get("endedAt") != null);

  }

  @Test
  public void aFormOfATaskWhichIsNotOpenOnItsLoanRequestGets404() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var otherLoanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);
    loanApproval.request(otherLoanRequestId, 7000);

    final var assessRiskTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();
    // The other loan request waits for its own risk assessment.
    awaitAggregate(
        loanApprovals,
        otherLoanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null);

    // An unknown id, the id of a task of another loan request, and the id of a task which is
    // not of this form.
    assertThat(refusalOf(() -> forms.getAssessRiskForm(loanRequestId, UUID.randomUUID().toString())))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);
    assertThat(refusalOf(() -> forms.getAssessRiskForm(otherLoanRequestId, assessRiskTaskId)))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);
    assertThat(refusalOf(() -> forms.completeAssessRisk(
        otherLoanRequestId,
        assessRiskTaskId,
        new AssessRiskInput("acceptable"))))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);
    assertThat(refusalOf(() -> forms.getCheckDocumentsForm(loanRequestId, assessRiskTaskId)))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);
    assertThat(refusalOf(() -> forms.completeCheckDocuments(
        loanRequestId,
        assessRiskTaskId,
        new CheckDocumentsInput("complete"))))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);

    // Completed once, the task is gone. The same form sent twice does not complete anything.
    forms.completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput("acceptable"));
    assertThat(refusalOf(() -> forms.completeAssessRisk(
        loanRequestId,
        assessRiskTaskId,
        new AssessRiskInput("acceptable"))))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);
    assertThat(refusalOf(() -> forms.getAssessRiskForm(loanRequestId, assessRiskTaskId)))
        .isEqualTo(BcTaskNotOpenException.HTTP_STATUS_CODE);

  }

  @Test
  public void aTaskWhichSomebodyElseTookOverGets409() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var assessRiskTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    // Nobody took the task over yet, so anybody's form may complete it. Here it is refused for
    // the empty input only, which leaves the task open for the rest of the test.
    final var formsOfAnna = formsOf("anna");
    assertThat(refusalOf(() -> formsOfAnna.completeAssessRisk(
        loanRequestId,
        assessRiskTaskId,
        new AssessRiskInput(" "))))
        .isEqualTo(HttpStatus.BAD_REQUEST.value());

    // 'bob' takes the task over in the cockpit. The form of 'anna' is still on her screen.
    CockpitGuiApi.start().assign(assessRiskTaskId, "bob");
    assertThat(refusalOf(() -> formsOfAnna.completeAssessRisk(
        loanRequestId,
        assessRiskTaskId,
        new AssessRiskInput("acceptable"))))
        .isEqualTo(BcTaskClaimedByAnotherUserException.HTTP_STATUS_CODE);

    // Reading takes nothing away from 'bob', and the task is still open for him.
    assertThat(formsOfAnna.getAssessRiskForm(loanRequestId, assessRiskTaskId).riskAssessment()).isNull();
    assertThat(loanApprovals.findById(loanRequestId).orElseThrow().getRiskAssessment()).isNull();
    formsOf("bob").completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput("acceptable"));

  }

  @Test
  public void anEmptyInputDoesNotCompleteTheTask() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var assessRiskTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    assertThat(refusalOf(() -> forms.completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput(" "))))
        .isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(refusalOf(() -> forms.completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput(null))))
        .isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(forms.getAssessRiskForm(loanRequestId, assessRiskTaskId).riskAssessment()).isNull();

  }

  /**
   * The interface of the endpoints is generated from the OpenAPI the frontends generate their
   * client from. Its paths are relative to the server of the file, and the controller puts the
   * path of the module in front. This checks the result: every operation in the file has its
   * endpoint at the path the cockpit forwards to.
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

    assertThat(specified).hasSize(4);
    assertThat(offered).containsAll(specified);

  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> details(
      final Map<String, Object> userTask) {

    final var details = (Map<String, Object>) userTask.get("details");
    return details == null ? Map.of() : details;

  }

  /**
   * The forms as a user of the cockpit sends them: with the cookie the browser sends. The OpenAPI
   * file does not name the cookie, because the browser adds it on its own, so the client sends it
   * with every request.
   *
   * @param user The user logged in to the cockpit, or null for nobody.
   * @return The client of the forms.
   */
  private UserTasksApi formsOf(
      final String user) {

    // The paths of the OpenAPI file are relative to its server, which the cockpit forwards to
    // the path of the module.
    final var restClient = RestClient
        .builder()
        .baseUrl("http://localhost:"
            + port
            + "/loan-approval");
    if (user != null) {
      restClient.defaultHeader(HttpHeaders.COOKIE, "bc="
          + user);
    }
    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient.build()))
        .build()
        .createClient(UserTasksApi.class);

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
