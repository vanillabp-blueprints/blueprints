package blueprint.workflowmodule.loanapproval;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

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
@QuarkusTest
@WithTestResource(LoanApprovalIT.ReportToTheSimulator.class)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  private RequestSpecification forms;

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it.
   */
  public static class ReportToTheSimulator implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {

      return Map.of(
          "vanillabp.cockpit.rest.base-url", CockpitSimulator.start().baseUrl(),
          Cockpit.ADDRESS, CockpitGuiApi.start().address());

    }

    @Override
    public void stop() {

      // Both end with the test run, through their shutdown hooks.

    }

  }

  @BeforeEach
  public void createClient() {

    forms = new RequestSpecBuilder()
        .setBasePath("/loan-approval/api/user-tasks")
        .build();

  }

  @Test
  public void bothUserTasksAreShownInTheCockpitAndCompletedByTheirForms() {

    final var cockpit = CockpitSimulator.start();
    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    // The first user task is open, and the cockpit shows it under the same id.
    final var assessRiskTaskId = awaitAggregate(
        loanApprovals::findByIdOptional,
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
        loanApprovals::findByIdOptional,
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
        loanApprovals::findByIdOptional,
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
        loanApprovals::findByIdOptional,
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
  public void aTaskWhichSomebodyElseTookOverGets409() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var assessRiskTaskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    // Nobody took the task over yet, so anybody's form may complete it. Here it is refused for
    // the empty input only, which leaves the task open for the rest of the test.
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", " "), "anna"))
        .isEqualTo(400);

    // 'bob' takes the task over in the cockpit. The form of 'anna' is still on her screen.
    CockpitGuiApi.start().assign(assessRiskTaskId, "bob");
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", "acceptable"), "anna"))
        .isEqualTo(409);

    // Reading takes nothing away from 'bob', and the task is still open for him.
    assertThat(status(assessRiskTaskId, "assess-risk")).isEqualTo(200);
    assertThat(loanApprovals.findByIdOptional(loanRequestId).orElseThrow().getRiskAssessment()).isNull();
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", "acceptable"), "bob"))
        .isEqualTo(204);

  }

  @Test
  public void anEmptyInputDoesNotCompleteTheTask() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var assessRiskTaskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    assertThat(send(assessRiskTaskId, "assess-risk", Map.of("riskAssessment", " ")))
        .isEqualTo(400);
    assertThat(send(assessRiskTaskId, "assess-risk", Map.of())).isEqualTo(400);
    assertThat(status(assessRiskTaskId, "assess-risk")).isEqualTo(200);

  }

  /**
   * The interface of the endpoints is generated from the OpenAPI the frontends generate their
   * client from. Its paths are relative to the server of the file, and the controller puts the
   * path of the module in front. This checks the result: every operation in the file has its
   * endpoint at the path the cockpit forwards to. Quarkus answers {@code OPTIONS} for a path it
   * has an endpoint for, and names the methods of that path in the header {@code Allow}.
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
    final Set<String> specified = new HashSet<>();
    ((Map<String, Map<String, Object>>) openApi.get("paths")).forEach((
        path,
        operations) -> {
      final var allowed = given()
          .options(pathOfTheModule + path.replace("{userTaskId}", "any-user-task"))
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

    assertThat(specified).hasSize(4);
    assertThat(offered).containsAll(specified);

  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> details(
      final Map<String, Object> userTask) {

    final var details = (Map<String, Object>) userTask.get("details");
    return details == null ? Map.of() : details;

  }

  private Map<String, Object> read(
      final String userTaskId,
      final String form) {

    return given(forms)
        .get("/{userTaskId}/{form}", userTaskId, form)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getMap("$");

  }

  private int status(
      final String userTaskId,
      final String form) {

    return given(forms)
        .get("/{userTaskId}/{form}", userTaskId, form)
        .statusCode();

  }

  /** Sends a form without a login, like a person who follows a URL of the log. */
  private int send(
      final String userTaskId,
      final String form,
      final Map<String, Object> input) {

    return send(userTaskId, form, input, null);

  }

  /** Sends a form as a user of the cockpit, with the cookie the browser sends. */
  private int send(
      final String userTaskId,
      final String form,
      final Map<String, Object> input,
      final String user) {

    final var request = given(forms)
        .contentType(ContentType.JSON)
        .body(input);
    if (user != null) {
      request.header(HttpHeaders.COOKIE, "bc="
          + user);
    }
    return request
        .post("/{userTaskId}/{form}", userTaskId, form)
        .statusCode();

  }

}
