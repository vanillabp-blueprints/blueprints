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

import blueprint.application.client.StartFormApi;
import blueprint.application.client.model.LoanRequest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The second half of the smoke test: does the application serve what the cockpit asks it for?
 *
 * <p>
 * The cockpit asks for two things below the path of the module's {@code workflow-module-uri}.
 * One is the manifest of the user interface, which comes from the JAR of the user interface.
 * The other is the API of the start form, which comes from the workflow module. A missing
 * dependency or a path which does not fit the module id breaks one of them, and the
 * application still starts. So this test asks for both, the way the cockpit does after it
 * replaced {@code /wm/loan-approval} by {@code /loan-approval}.
 * </p>
 *
 * <p>
 * The start form is called through a client which the build generates from the OpenAPI file in
 * the JAR of the workflow module. So the test writes no path and no field of the API by hand.
 * The manifest is a file of the user interface and not part of an API, so a plain RestClient
 * reads it.
 * </p>
 *
 * <p>
 * The workflow module has an integration test of its own, which checks that the new case
 * reaches the cockpit. This test only checks that the parts fit together in this application.
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

  /** The start form, which the user interface calls. */
  private StartFormApi startForm;

  @BeforeEach
  public void createClients() {

    module = RestClient.create("http://localhost:"
        + port
        + "/loan-approval");
    // The paths of 'loan-approval.yaml' are relative to its server, which the cockpit forwards to
    // the path of the module.
    startForm = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(module))
        .build()
        .createClient(StartFormApi.class);

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
        .containsKeys("Header", "StartForm");

  }

  @Test
  public void theStartFormStartsALoanApproval() {

    final var loanRequestId = startForm
        .requestLoan(new LoanRequest("Jane Doe", 5000))
        .loanRequestId();

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> loanApprovals
                .findById(loanRequestId)
                .map(Aggregate::getReviewTaskId)
                .orElse(null),
            Objects::nonNull);

  }

}
