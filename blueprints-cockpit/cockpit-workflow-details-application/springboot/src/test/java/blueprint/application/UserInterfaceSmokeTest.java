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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

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
 * The workflow module has an integration test of its own, which withdraws a loan request. This
 * test only checks that the parts fit together in this application.
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

  private RestClient module;

  @BeforeEach
  public void createClient() {

    module = RestClient.create("http://localhost:"
        + port
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
        .containsKeys("WorkflowPage", "WorkflowList");

  }

  @Test
  public void theCasePageOfARunningCaseAnswers() {

    final var loanRequestId = module
        .get()
        .uri("/api/loan-requests/start?applicant=Jane Doe&amount=5000")
        .retrieve()
        .body(String.class);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> loanApprovals
                .findById(loanRequestId)
                .map(Aggregate::getReviewTaskId)
                .orElse(null),
            Objects::nonNull);

    final var page = module
        .get()
        .uri("/api/cases/{loanRequestId}", loanRequestId)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {
        });

    assertThat(page)
        .containsEntry("loanRequestId", loanRequestId)
        .containsEntry("applicant", "Jane Doe")
        .containsEntry("amount", 5000)
        .containsEntry("status", "REQUESTED");

  }

}
