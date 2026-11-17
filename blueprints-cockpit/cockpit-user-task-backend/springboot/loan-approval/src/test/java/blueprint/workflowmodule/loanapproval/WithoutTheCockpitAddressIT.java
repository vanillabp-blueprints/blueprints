package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.client.UserTasksApi;
import blueprint.workflowmodule.loanapproval.client.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The module in an application which does not set {@code loan-approval.cockpit.base-url}. The
 * application starts, the module has no client of the cockpit, and a form completes its task
 * without asking anybody, even with the cookie of a user and a task somebody else took over in
 * the cockpit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WithoutTheCockpitAddressIT extends WorkflowModuleTest {

  @Autowired
  private Service loanApproval;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private ObjectProvider<Cockpit.Tasklist> tasklist;

  @Value("${local.server.port}")
  private int port;

  /**
   * Starts the simulator before the application, and points the cockpit adapter at it. The
   * address of the official GUI API stays unset.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void reportToTheSimulator(
      final DynamicPropertyRegistry properties) {

    properties.add("vanillabp.cockpit.rest.base-url", () -> CockpitSimulator.start().baseUrl());

  }

  @Test
  public void aFormCompletesItsTaskWithoutAskingTheCockpit() {

    assertThat(tasklist.getIfAvailable()).isNull();

    final var loanRequestId = UUID.randomUUID().toString();
    loanApproval.request(loanRequestId, 5000);
    final var assessRiskTaskId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getAssessRiskTaskId() != null)
        .getAssessRiskTaskId();

    // 'bob' has the task in the stand-in of the cockpit, but nobody asks it.
    CockpitGuiApi.start().assign(assessRiskTaskId, "bob");
    formsOf("anna").completeAssessRisk(loanRequestId, assessRiskTaskId, new AssessRiskInput("acceptable"));

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> "acceptable".equals(aggregate.getRiskAssessment()));

  }

  private UserTasksApi formsOf(
      final String user) {

    // The paths of the OpenAPI file are relative to its server, which the cockpit forwards to
    // the path of the module.
    final var restClient = RestClient
        .builder()
        .baseUrl("http://localhost:"
            + port
            + "/loan-approval")
        .defaultHeader(HttpHeaders.COOKIE, "bc="
            + user)
        .build();
    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(UserTasksApi.class);

  }

}
