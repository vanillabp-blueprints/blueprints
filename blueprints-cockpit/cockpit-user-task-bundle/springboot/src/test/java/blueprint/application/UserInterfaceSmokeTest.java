package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.application.client.LoanRequestsApi;
import blueprint.application.client.UserTasksApi;
import blueprint.application.client.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The second half of the smoke test: does an application which takes the bundle serve what the
 * cockpit asks it for?
 *
 * <p>
 * The cockpit asks for two things below the path of the module's {@code workflow-module-uri}.
 * One is the manifest of the user interface, which comes from the JAR of the user interface.
 * The other is the API of the forms, which comes from the workflow module. A missing
 * dependency or a path which does not fit the module id breaks one of them, and the
 * application still starts. So this test asks for both, the way the cockpit does after it
 * replaced {@code /wm/loan-approval} by {@code /loan-approval}.
 * </p>
 *
 * <p>
 * The API calls go through clients which the build generates from the OpenAPI files in the JAR
 * of the workflow module. So the test writes no path and no field of the API by hand. The
 * manifest is a file of the user interface and not part of an API, so a plain RestClient reads
 * it.
 * </p>
 *
 * <p>
 * The workflow module has an integration test of its own, which plays both user tasks
 * through. This test only checks that the bundle brings everything an application needs.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserInterfaceSmokeTest {

  @Autowired
  private AggregateRepository loanApprovals;

  /**
   * The part of {@code bc-manifest.json} this test reads: the name of each entry and the file
   * which holds it.
   *
   * @param entries The entries by name.
   */
  record Manifest(Map<String, String> entries) {
  }

  @Value("${local.server.port}")
  private int port;

  /** The files below the path of the module. */
  private RestClient module;

  /** Starts a loan approval. */
  private LoanRequestsApi loanRequests;

  /** The endpoints the user interface calls. */
  private UserTasksApi userInterface;

  @BeforeEach
  public void createClients() {

    final var application = "http://localhost:"
        + port;
    module = RestClient.create(application
        + "/loan-approval");
    // The server of 'loan-requests.yaml' ends with the path of the module, so its paths start with
    // /api.
    loanRequests = clientOf(LoanRequestsApi.class, application
        + "/loan-approval");
    // The paths of 'loan-approval.yaml' are relative to its server, which the cockpit forwards to
    // the path of the module.
    userInterface = clientOf(UserTasksApi.class, application
        + "/loan-approval");

  }

  @Test
  public void theManifestComesFromTheJarOfTheUserInterface() {

    final var manifest = module
        .get()
        .uri("/bc-manifest.json")
        .retrieve()
        .body(Manifest.class);

    assertThat(manifest.entries())
        .describedAs("The manifest lists the entries the cockpit loads. If it is missing, the"
            + " JAR of the user interface is not on the class path, or its files are not below"
            + " META-INF/resources/loan-approval/.")
        .containsKeys("UserTaskForm", "UserTaskList");

  }

  @Test
  public void theFormOfAnOpenUserTaskAnswers() {

    final var loanRequestId = loanRequests.start(5000);

    final var userTaskId = await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> loanApprovals
                .findById(loanRequestId)
                .map(Aggregate::getAssessRiskTaskId)
                .orElse(null),
            Objects::nonNull);

    assertThat(userInterface.getAssessRiskForm(loanRequestId, userTaskId))
        .isEqualTo(new AssessRiskForm(loanRequestId, 5000, null));

  }

  private static <T> T clientOf(
      final Class<T> api,
      final String baseUrl) {

    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(RestClient.create(baseUrl)))
        .build()
        .createClient(api);

  }

}
