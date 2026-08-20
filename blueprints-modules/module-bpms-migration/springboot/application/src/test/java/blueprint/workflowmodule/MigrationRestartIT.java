package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import blueprint.workflowmodule.loanapproval.Service;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The migration itself: the application is stopped, its configuration changes, and from then
 * on new workflows start in the other BPMS.
 *
 * <p>
 * Two workflows exist before that happens and two after, and every one of them is driven to
 * its end afterwards. That the older ones still finish is the whole point: their user task
 * and their message are answered in the BPMS they were started in, while new workflows
 * already start in the new one.
 * </p>
 *
 * <p>
 * This is why the blueprint uses a file database. Both boots share it, so the aggregates and
 * the tables of the embedded engine outlive the first application, exactly as a real
 * deployment would. The test brings its own file and deletes it first, so a repeated run
 * starts from nothing.
 * </p>
 *
 * <p>
 * The test runs where two adapters are configured, which is the {@code camunda8} profile.
 * With a single BPMS there is nothing to migrate to.
 * </p>
 */
public class MigrationRestartIT {

  private static final String NEW_BPMS = "camunda8";

  private static final String OLD_BPMS = "camunda7";

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Path DATABASE = Path.of("target", "database", "migration-restart");

  @Test
  @DisplayName("Workflows of the old BPMS finish there while new ones start in the new BPMS")
  public void theOldWorkflowsKeepRunningWhereTheyWereStarted() throws Exception {

    // the migration needs a second BPMS to migrate to, which is what the priority list of
    // the 'camunda8' profile brings
    assumeTrue(
        NEW_BPMS.equals(System.getProperty("spring.profiles.active")),
        "these tests need two adapters, which is what the 'camunda8' profile configures");

    emptyDatabase();

    final String firstOfTheOld;
    final String secondOfTheOld;

    // BEFORE the migration: loan approvals still start in the old BPMS, said by the priority
    // list of that workflow. The adapter list stays as it is on purpose. Taking the new
    // adapter out of the configuration would be the other way to write this, but then an
    // environment variable addressing that adapter id ends the boot, and handing the address
    // of a cluster in through the environment is exactly what the CI does.
    try (var application = boot(
        "--vanillabp.workflow-modules.loan-approval.workflows.loan_approval.prioritized-adapters="
            + OLD_BPMS)) {

      final var service = application.getBean(Service.class);
      final var aggregates = application.getBean(AggregateRepository.class);

      firstOfTheOld = started(service);
      secondOfTheOld = started(service);

      // one of the two is taken past its user task, so it waits for the message
      final var waiting = await(
          aggregates,
          firstOfTheOld,
          aggregate -> aggregate.getRiskAssessmentTaskId() != null);
      service.assessRisk(firstOfTheOld, waiting.getRiskAssessmentTaskId());
      await(aggregates, firstOfTheOld, aggregate -> aggregate.getRiskAssessmentTaskId() == null);

      // the other one is left at its user task
      await(aggregates, secondOfTheOld, aggregate -> aggregate.getRiskAssessmentTaskId() != null);

      assertThat(service.bpmsHolding(firstOfTheOld)).contains(OLD_BPMS);
    }

    // AFTER the migration: the priority list of the workflow is gone, so the adapter list
    // applies, and that one names the new BPMS first. Nothing else changes.
    try (var application = boot()) {

      final var service = application.getBean(Service.class);
      final var aggregates = application.getBean(AggregateRepository.class);

      final var ofTheNew = started(service);
      final var waitingInTheNew = await(
          aggregates,
          ofTheNew,
          aggregate -> aggregate.getRiskAssessmentTaskId() != null);

      assertThat(service.bpmsHolding(ofTheNew))
          .describedAs("a new workflow starts in the first adapter of the list")
          .contains(NEW_BPMS);
      assertThat(service.bpmsHolding(secondOfTheOld))
          .describedAs("a workflow started before the migration is still held by the old BPMS")
          .contains(OLD_BPMS);

      // the user task of the workflow left waiting in the old BPMS
      final var waitingInTheOld = aggregates
          .findById(secondOfTheOld)
          .orElseThrow();
      service.assessRisk(secondOfTheOld, waitingInTheOld.getRiskAssessmentTaskId());
      await(aggregates, secondOfTheOld, aggregate -> aggregate.getRiskAssessmentTaskId() == null);

      // the message for the workflow which was already waiting for it, also in the old BPMS
      service.contractSigned(firstOfTheOld, "Jane Doe");
      final var paidOut = await(
          aggregates,
          firstOfTheOld,
          aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));
      assertThat(paidOut.getContractSignedBy()).isEqualTo("Jane Doe");

      // and the same two operations for the workflow of the new BPMS
      service.assessRisk(ofTheNew, waitingInTheNew.getRiskAssessmentTaskId());
      await(aggregates, ofTheNew, aggregate -> aggregate.getRiskAssessmentTaskId() == null);
      service.contractSigned(ofTheNew, "John Doe");
      await(aggregates, ofTheNew, aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

      // the second one of the old BPMS is answered last, to show the routing keeps working
      service.contractSigned(secondOfTheOld, "Jane Doe");
      await(aggregates, secondOfTheOld, aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

      assertThat(service.bpmsHolding(secondOfTheOld))
          .describedAs("it ran to its end where it was started")
          .contains(OLD_BPMS);
    }

  }

  /**
   * @param arguments What this boot configures differently
   * @return The running application, to be closed by the caller
   */
  private ConfigurableApplicationContext boot(
      final String... arguments) {

    final var all = new java.util.ArrayList<String>(List.of(arguments));
    all.add("--spring.datasource.url=jdbc:h2:file:./"
        + DATABASE
        + ";AUTO_SERVER=TRUE");
    all.add("--server.port=0");
    return new SpringApplicationBuilder(Application.class)
        .run(all.toArray(String[]::new));

  }

  private static String started(
      final Service service) {

    final var loanRequestId = UUID.randomUUID().toString();
    service.initiateLoanApproval(loanRequestId, 5000);
    return loanRequestId;

  }

  /**
   * Waiting rather than asserting right away: a BPMS gets to a task eventually, and a remote
   * one takes its time.
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
