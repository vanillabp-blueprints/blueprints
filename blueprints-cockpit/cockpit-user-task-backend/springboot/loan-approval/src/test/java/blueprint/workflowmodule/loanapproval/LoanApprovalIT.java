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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

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

  private RestClient forms;

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

    forms = RestClient.create("http://localhost:"
        + port
        + "/loan-approval/api/user-tasks");

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
    assertThat(read(assessRiskTaskId, "assess-risk"))
        .containsEntry("loanRequestId", loanRequestId)
        .containsEntry("amount", 5000)
        .doesNotContainKey("riskAssessment");
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", "acceptable")))
        .isEqualTo(204);

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

    assertThat(read(checkDocumentsTaskId, "check-documents"))
        .containsEntry("loanRequestId", loanRequestId)
        .containsEntry("riskAssessment", "acceptable")
        .doesNotContainKey("documentsNote");
    assertThat(send(checkDocumentsTaskId, "check-documents", Map.of("documentsNote", "complete")))
        .isEqualTo(204);

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
  public void aFormOfATaskWhichIsNotOpenGets404() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var assessRiskTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    // An unknown id, and the id of a task which is not of this form.
    assertThat(status(UUID.randomUUID().toString(), "assess-risk")).isEqualTo(404);
    assertThat(status(assessRiskTaskId, "check-documents")).isEqualTo(404);
    assertThat(send(assessRiskTaskId, "check-documents", Map.of("documentsNote", "complete")))
        .isEqualTo(404);

    // Completed once, the task is gone. The same form sent twice does not complete anything.
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", "acceptable")))
        .isEqualTo(204);
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", "acceptable")))
        .isEqualTo(404);
    assertThat(status(assessRiskTaskId, "assess-risk")).isEqualTo(404);

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

    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", " ")))
        .isEqualTo(400);
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of())).isEqualTo(400);
    assertThat(status(assessRiskTaskId, "assess-risk")).isEqualTo(200);

  }

  /**
   * The endpoints are written by hand. This keeps them in line with the OpenAPI the frontends
   * generate their client from: every operation in the file has its endpoint here.
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> read(
      final String userTaskId,
      final String form) {

    return forms
        .get()
        .uri("/{userTaskId}/{form}", userTaskId, form)
        .retrieve()
        .body(Map.class);

  }

  private int status(
      final String userTaskId,
      final String form) {

    return forms
        .get()
        .uri("/{userTaskId}/{form}", userTaskId, form)
        .exchange((
            request,
            response) -> response.getStatusCode().value());

  }

  private int send(
      final String userTaskId,
      final String form,
      final Map<String, Object> input) {

    return forms
        .post()
        .uri("/{userTaskId}/{form}", userTaskId, form)
        .contentType(MediaType.APPLICATION_JSON)
        .body(input)
        .exchange((
            request,
            response) -> response.getStatusCode().value());

  }

}
