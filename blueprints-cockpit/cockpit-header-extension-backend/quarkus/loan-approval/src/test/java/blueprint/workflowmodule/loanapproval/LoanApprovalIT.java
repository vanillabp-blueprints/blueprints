package blueprint.workflowmodule.loanapproval;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalModuleDetails;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vanillabp.spi.cockpit.workflowmodules.WorkflowModuleDetailsProvider;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

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
 */
@QuarkusTest
@WithTestResource(LoanApprovalIT.ReportToTheSimulator.class)
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  @Any
  Instance<WorkflowModuleDetailsProvider> workflowModuleDetails;

  private RequestSpecification startForm;

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

    startForm = new RequestSpecBuilder()
        .setBasePath("/loan-approval/api/loan-requests")
        .build();

  }

  @Test
  @SuppressWarnings("unchecked")
  public void theStartFormStartsALoanApprovalAndTheCaseIsInTheCaseList() {

    final var cockpit = CockpitSimulator.start();

    // The button "OK" of the start form.
    final var started = given(startForm)
        .contentType(ContentType.JSON)
        .body(Map.of("applicant", "Jane Doe", "amount", 5000))
        .post();
    assertThat(started.statusCode()).isEqualTo(201);
    final var loanRequestId = started
        .jsonPath()
        .getString("loanRequestId");
    assertThat(loanRequestId).isNotBlank();

    // The module started the process, and the process waits in the user task.
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
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

    assertThat(post(Map.of("applicant", " ", "amount", 5000))).isEqualTo(400);
    assertThat(post(Map.of("applicant", "Jane Doe", "amount", 0))).isEqualTo(400);
    assertThat(post(Map.of("applicant", "Jane Doe"))).isEqualTo(400);

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

    final var all = workflowModuleDetails
        .stream()
        .toList();
    assertThat(all).hasSize(1);
    final var details = all.getFirst();
    assertThat(details.getWorkflowModuleId()).isEqualTo("loan-approval");
    assertThat(details.getAccessibleToGroups()).containsExactly(LoanApprovalModuleDetails.LOAN_OFFICERS);

  }

  @Test
  public void anApprovedLoanRequestEndsTheCase() {

    final var cockpit = CockpitSimulator.start();

    final var loanRequestId = loanApproval.request("John Doe", 7000);
    awaitAggregate(loanApprovals::findByIdOptional, loanRequestId, aggregate -> aggregate.getReviewTaskId() != null);

    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.DONE);
    assertThat(loanApproval.approve(loanRequestId)).isEqualTo(Service.Change.NOT_POSSIBLE);

    final var ended = cockpit.awaitWorkflow(loanRequestId, workflow -> workflow.get("endedAt") != null);
    assertThat(ended.get("endReason")).isEqualTo("COMPLETED");

  }

  /**
   * The interface of the endpoint is generated from the OpenAPI the frontend generates its
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
          .options(pathOfTheModule + path)
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

    assertThat(specified).hasSize(1);
    assertThat(offered).containsAll(specified);

  }

  private int post(
      final Map<String, Object> body) {

    return given(startForm)
        .contentType(ContentType.JSON)
        .body(body)
        .post()
        .statusCode();

  }

}
