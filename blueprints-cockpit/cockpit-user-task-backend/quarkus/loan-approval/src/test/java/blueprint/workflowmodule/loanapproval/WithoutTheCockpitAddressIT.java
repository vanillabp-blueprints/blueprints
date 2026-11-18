package blueprint.workflowmodule.loanapproval;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.client.UserTasksApi;
import blueprint.workflowmodule.loanapproval.client.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * The module in an application which does not set {@code loan-approval.cockpit.base-url}. The
 * application starts, the module builds no client of the cockpit, and a form completes its task
 * without asking anybody, even with the cookie of a user and a task somebody else took over in
 * the cockpit.
 */
@QuarkusTest
@WithTestResource(WithoutTheCockpitAddressIT.ReportToTheSimulator.class)
public class WithoutTheCockpitAddressIT extends WorkflowModuleTest {

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

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it. The
   * address of the official GUI API stays unset.
   */
  public static class ReportToTheSimulator implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {

      return Map.of("vanillabp.cockpit.rest.base-url", CockpitSimulator.start().baseUrl());

    }

    @Override
    public void stop() {

      // The simulator ends with the test run, through its shutdown hook.

    }

  }

  @Test
  public void aFormCompletesItsTaskWithoutAskingTheCockpit() {

    final var loanRequestId = UUID.randomUUID().toString();
    loanApproval.request(loanRequestId, 5000);
    final var assessRiskTaskId = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    // 'bob' has the task in the stand-in of the cockpit, but nobody asks it.
    CockpitGuiApi.start().assign(assessRiskTaskId, "bob");
    QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(module)
        .register((ClientRequestFilter) request -> request
            .getHeaders()
            .add(HttpHeaders.COOKIE, "bc=anna"))
        .build(UserTasksApi.class)
        .completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput("acceptable"));

    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> "acceptable".equals(aggregate.getRiskAssessment()));

  }

}
