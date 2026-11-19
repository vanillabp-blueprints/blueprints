package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blueprint.application.client.CasesApi;
import blueprint.application.client.LoanRequestsApi;
import blueprint.application.client.model.Case;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

/**
 * The second half of the smoke test: does the application serve what the cockpit asks it for?
 *
 * <p>
 * The cockpit asks for two things below the path of the module's {@code workflow-module-uri}.
 * One is the manifest of the user interface, which comes from the JAR of the user interface.
 * The other is the API of the case page, which comes from the workflow module. A missing
 * dependency or a path which does not fit the module id breaks one of them, and the
 * application still starts. So this test asks for both, the way the cockpit does after it
 * replaced {@code /wm/loan-approval} by {@code /loan-approval}.
 * </p>
 *
 * <p>
 * The API calls go through clients which the build generates from the OpenAPI files in the JAR
 * of the workflow module. So the test writes no path and no field of the API by hand. The
 * manifest is a file of the user interface and not part of an API, so a plain client of Jakarta
 * REST reads it.
 * </p>
 *
 * <p>
 * The workflow module has an integration test of its own, which withdraws a loan request. This
 * test only checks that the parts fit together in this application.
 * </p>
 */
@QuarkusTest
public class UserInterfaceSmokeTest {

  @Inject
  AggregateRepository loanApprovals;

  /**
   * The part of {@code bc-manifest.json} this test reads: the name of each entry and the file
   * which holds it.
   *
   * @param entries The entries by name.
   */
  record Manifest(Map<String, String> entries) {
  }

  /** The path of the module in the application under test, on its free port. */
  @TestHTTPResource("/loan-approval")
  URI pathOfTheModule;

  /** The files below the path of the module. */
  private WebTarget module;

  /** Starts a loan approval. */
  private LoanRequestsApi loanRequests;

  /** The API of the case page, which the user interface calls. */
  private CasesApi casePage;

  @BeforeEach
  public void createClients() {

    module = ClientBuilder
        .newClient()
        .target(pathOfTheModule);
    // The server of 'loan-requests.yaml' ends with the path of the module, so its paths start with
    // /api.
    loanRequests = clientOf(LoanRequestsApi.class, pathOfTheModule);
    // The paths of 'loan-approval.yaml' are relative to its server, which the cockpit forwards to
    // the path of the module.
    casePage = clientOf(CasesApi.class, pathOfTheModule);

  }

  @Test
  public void theManifestComesFromTheJarOfTheUserInterface() {

    final var manifest = module
        .path("/bc-manifest.json")
        .request()
        .get(Manifest.class);

    assertThat(manifest.entries())
        .describedAs("The manifest lists the entries the cockpit loads. If it is missing, the"
            + " JAR of the user interface is not on the class path, or its files are not below"
            + " META-INF/resources/loan-approval/.")
        .containsKeys("WorkflowPage", "WorkflowList");

  }

  @Test
  public void theCasePageOfARunningCaseAnswers() {

    final var loanRequestId = loanRequests.start("Jane Doe", 5000);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> QuarkusTransaction
                .requiringNew()
                .call(() -> loanApprovals
                    .findByIdOptional(loanRequestId)
                    .map(Aggregate::getReviewTaskId)
                    .orElse(null)),
            Objects::nonNull);

    final Case loanRequest = casePage.getCase(loanRequestId);
    assertThat(loanRequest.getLoanRequestId()).isEqualTo(loanRequestId);
    assertThat(loanRequest.getApplicant()).isEqualTo("Jane Doe");
    assertThat(loanRequest.getAmount()).isEqualTo(5000);
    assertThat(loanRequest.getStatus()).isEqualTo(Case.StatusEnum.REQUESTED);

  }

  private static <T> T clientOf(
      final Class<T> api,
      final URI baseUri) {

    return QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(baseUri)
        .build(api);

  }

}
