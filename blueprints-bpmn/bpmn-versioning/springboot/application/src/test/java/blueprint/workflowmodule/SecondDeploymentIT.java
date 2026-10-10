package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import blueprint.workflowmodule.loanapproval.Service;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * What happens when a changed model is deployed while a workflow still runs on the old one.
 *
 * <p>
 * The test boots the application twice against one file database. The first boot deploys
 * the model as it was before the change, which becomes version 1, and starts a workflow on
 * it. That model waits a few seconds before the risk is assessed, so the workflow is still
 * waiting when the first boot stops. The second boot deploys the model the blueprint ships,
 * which becomes version 2.
 * </p>
 *
 * <p>
 * Then the test checks two things. The start of the second boot says that one workflow
 * still runs on an older version. And that workflow ends through the method kept for
 * version 1, while a workflow started now ends through the method for the versions after
 * it.
 * </p>
 *
 * <p>
 * The test runs where the engine is the test's own, which is the {@code camunda7} profile.
 * The dispatch is checked by version number, and only an engine which starts empty counts
 * from one. A Camunda 8 cluster keeps every version deployed before, the one of the module's
 * own test included, so there the old model would not be version 1.
 * </p>
 */
@ExtendWith(OutputCaptureExtension.class)
public class SecondDeploymentIT {

  private static final String BPMS = "camunda7";

  /**
   * Where the first boot reads its BPMN files: the model as it was before the change. The
   * second boot reads the model the blueprint ships, from where VanillaBP looks by default.
   */
  private static final String VERSION_1_MODEL = "classpath*:version-1/"
      + BPMS;

  /**
   * Long enough for the wait in the old model, the restart and the task after it.
   */
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  private static final Path DATABASE = Path.of("target", "database", "second-deployment");

  @Test
  @DisplayName("A workflow of the old version ends on the method kept for it")
  public void theOldWorkflowEndsOnTheMethodKeptForIt(
      final CapturedOutput output) throws Exception {

    assumeTrue(
        BPMS.equals(System.getProperty("spring.profiles.active")),
        "this test counts versions from one, which needs an engine of the test's own");

    emptyDatabase();

    final String ofVersion1;

    // FIRST deployment: the old model. The workflow stops at the wait before the risk
    // assessment, and the application is stopped before the wait is over.
    try (var application = boot(
        "--vanillabp.workflow-modules.loan-approval.adapters."
            + BPMS
            + ".resources-location="
            + VERSION_1_MODEL)) {

      final var loanApproval = application.getBean(Service.class);
      final var aggregates = application.getBean(AggregateRepository.class);

      ofVersion1 = started(loanApproval);
      final var waiting = await(aggregates, ofVersion1, aggregate -> true);
      assertThat(waiting.getCreditRating())
          .describedAs("the workflow still waits before its risk is assessed")
          .isNull();
    }

    final var outputBeforeSecondBoot = output
        .getOut()
        .length();

    // SECOND deployment: the model the blueprint ships. It differs from the old one, so the
    // engine counts it as version 2. The workflow of version 1 keeps its version.
    try (var application = boot()) {

      // The start says how many workflows still run on an older version. It is a notice and
      // not a warning: every task of version 1 is served, so nothing is wrong.
      final var startOfSecondBoot = output
          .getOut()
          .substring(outputBeforeSecondBoot);
      assertThat(startOfSecondBoot)
          .describedAs("the start names the workflow still running on version 1")
          .contains("process 'loan_approval' of workflow module 'loan-approval'")
          .contains("1 workflow(s) of this BPMN process still run on 1 version(s) older");

      final var loanApproval = application.getBean(Service.class);
      final var aggregates = application.getBean(AggregateRepository.class);

      final var ofVersion2 = started(loanApproval);

      final var old = await(
          aggregates,
          ofVersion1,
          aggregate -> aggregate.getCreditRating() != null);
      assertThat(old.getAssessedBy())
          .describedAs("the workflow of version 1 ran the method kept for version 1")
          .isEqualTo("the four eyes principle");
      assertThat(old.getRiskScore())
          .describedAs("the method of the later versions did not run for it")
          .isNull();

      final var current = await(
          aggregates,
          ofVersion2,
          aggregate -> aggregate.getCreditRating() != null);
      assertThat(current.getRiskScore())
          .describedAs("a workflow started now runs the method of the later versions")
          .isEqualTo(50);
      assertThat(current.getAssessedBy())
          .describedAs("the method of version 1 did not run for it")
          .isNull();
    }

  }

  /**
   * @param arguments What this boot configures differently
   * @return The running application, to be closed by the caller
   */
  private ConfigurableApplicationContext boot(
      final String... arguments) {

    final var all = new ArrayList<String>(List.of(arguments));
    all.add("--spring.datasource.url=jdbc:h2:file:./"
        + DATABASE
        + ";AUTO_SERVER=TRUE");
    // both boots share the tables, so the second one must not drop what the first wrote
    all.add("--spring.jpa.hibernate.ddl-auto=update");
    all.add("--server.port=0");
    return new SpringApplicationBuilder(Application.class)
        .run(all.toArray(String[]::new));

  }

  private static String started(
      final Service loanApproval) {

    final var loanRequestId = UUID.randomUUID().toString();
    loanApproval.request(loanRequestId, 5000);
    return loanRequestId;

  }

  /**
   * Waiting rather than asserting right away: a BPMS gets to a task eventually.
   *
   * @param aggregates    Where to read
   * @param loanRequestId Which workflow
   * @param condition     What is waited for
   * @return The aggregate once it fulfills the condition
   */
  private static Aggregate await(
      final AggregateRepository aggregates,
      final String loanRequestId,
      final Predicate<Aggregate> condition) {

    final var deadline = System.nanoTime() + TIMEOUT.toNanos();
    Aggregate last = null;
    while (System.nanoTime() < deadline) {
      last = aggregates
          .findById(loanRequestId)
          .orElse(null);
      if ((last != null) && condition.test(last)) {
        return last;
      }
      try {
        Thread.sleep(200);
      } catch (final InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      }
    }
    throw new AssertionError("The workflow '"
        + loanRequestId
        + "' did not reach the expected state within "
        + TIMEOUT
        + ". Last seen: "
        + last);

  }

  private static void emptyDatabase() throws Exception {

    Files.createDirectories(DATABASE.getParent());
    try (var files = Files.list(DATABASE.getParent())) {
      for (final var file : files.toList()) {
        if (file
            .getFileName()
            .toString()
            .startsWith(DATABASE.getFileName().toString())) {
          Files.delete(file);
        }
      }
    }

  }

}
