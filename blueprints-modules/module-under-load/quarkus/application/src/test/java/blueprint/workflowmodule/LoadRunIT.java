package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import blueprint.simulator.loadrun.api.client.LoadRunApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Proves that the load driver still starts and finishes: a tiny run, with every stage and the
 * drain.
 *
 * <p>
 * Two processes take part, as in a real run. This test starts the application, on the port
 * the build reserved for it and with a JMX port. The build started the simulator before, and
 * told it where the application will be. The test then starts a run through the API of the
 * simulator, as a person would in a browser, and reads the report the same way.
 * </p>
 *
 * <p>
 * It measures nothing. A shared CI runner moves its numbers more between two runs than a real
 * change would, so no number of this test is compared with anything. What it does assert is
 * what a run needs to be read at all: every workflow it started reached its end, nothing
 * failed, and the report has a row per stage.
 * </p>
 *
 * <p>
 * Started from the IDE, the test finds no simulator unless somebody started one, and then it
 * is skipped. Started by the build, the simulator is there by contract, so a missing one fails
 * the test.
 * </p>
 */
@QuarkusTest
@TestProfile(LoadRunIT.BothProcesses.class)
public class LoadRunIT {

  /** Where the application listens and where the credit bureau is, and a faster payout run. */
  public static class BothProcesses implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {

      return Map.of(
          "quarkus.http.test-port", "${application.port:8080}",
          "credit-bureau.base-url", "${simulator.base-url:http://localhost:8079}/credit-bureau",
          "credit-bureau.callback-url", "http://localhost:${application.port:8080}/credit-bureau/callback",
          // The drain waits for the payout run, so it comes more often here.
          "loan-approval.payout-run-every", "PT5S");

    }

  }

  /** How long the build gives a simulator it started to answer. */
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

  /** How long a tiny run may take, with its drain. */
  private static final Duration RUN_TIMEOUT = Duration.ofMinutes(5);

  private final String simulator = System.getProperty("simulator.base-url", "http://localhost:8079");

  private final LoadRunApi loadRun = QuarkusRestClientBuilder
      .newBuilder()
      .baseUri(URI.create(simulator))
      .build(LoadRunApi.class);

  @Test
  public void aTinyRunStartsAndFinishes() {

    waitForTheSimulator();

    assertThat(loadRun.start("tiny"))
        .describedAs("The simulator started the run. Its log:%n%s", simulatorLog())
        .contains("started");

    final var report = await()
        .atMost(RUN_TIMEOUT)
        .pollInterval(Duration.ofSeconds(1))
        .until(
            loadRun::report,
            text -> text.startsWith("Load run 'tiny'") || text.startsWith("The run 'tiny' stopped"));

    assertThat(report)
        .describedAs("The run ended with a report. The log of the simulator:%n%s", simulatorLog())
        .startsWith("Load run 'tiny'");
    assertThat(report)
        .describedAs("Every workflow of the run ended, and nothing failed")
        .doesNotContain("NOT FINISHED")
        .doesNotContain("requests or decisions failed");
    // warm-up, steady, one step, the peak and the drain
    assertThat(report.lines())
        .filteredOn(line -> line.matches("(warm-up|steady|step-1|peak|drain) .*"))
        .hasSize(5);

  }

  private void waitForTheSimulator() {

    final var required = Boolean.getBoolean("simulator.required");
    final var giveUpAt = Instant
        .now()
        .plus(required ? STARTUP_TIMEOUT : Duration.ZERO);
    while (!simulatorAnswers()) {
      if (Instant
          .now()
          .isAfter(giveUpAt)) {
        if (required) {
          throw new IllegalStateException("The build started a simulator, but nothing answers at "
              + simulator
              + " after "
              + STARTUP_TIMEOUT
              + ". Its log:\n"
              + simulatorLog());
        }
        Assumptions.abort("No simulator answers at "
            + simulator
            + ". Run 'mvn verify', which starts one for this test.");
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread
            .currentThread()
            .interrupt();
        throw new IllegalStateException("Interrupted while waiting for the simulator", e);
      }
    }

  }

  private boolean simulatorAnswers() {

    try {
      loadRun.report();
      return true;
    } catch (RuntimeException e) {
      return false;
    }

  }

  private static String simulatorLog() {

    final var log = Path.of(System.getProperty("simulator.log", "target/simulator.log"));
    try {
      return Files.exists(log)
          ? Files.readString(log)
          : "(no log at '"
              + log.toAbsolutePath()
              + "', so this simulator was not started by the build)";
    } catch (IOException e) {
      return "(cannot read '"
          + log
          + "': "
          + e.getMessage()
          + ")";
    }

  }

}
