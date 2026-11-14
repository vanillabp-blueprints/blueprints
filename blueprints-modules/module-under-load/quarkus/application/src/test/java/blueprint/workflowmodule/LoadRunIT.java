package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import blueprint.loaddriver.LoadRun;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Proves that the load driver still starts and finishes: a tiny run, with every stage and the
 * drain.
 *
 * <p>
 * It measures nothing. A shared CI runner moves its numbers more between two runs than a real
 * change would, so no number of this test is compared with anything. What it does assert is
 * what a run needs to be read at all: every workflow it started reached its end, nothing
 * failed, and the report has a row per stage.
 * </p>
 */
@QuarkusTest
@TestProfile(LoadRunIT.TinyRun.class)
public class LoadRunIT {

  /** The run size of this test, and a payout run which comes more often, so the drain is short. */
  public static class TinyRun implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {

      return Map.of(
          "load-run.sizes.tiny.stage-length", "PT2S",
          "load-run.sizes.tiny.steps", "1",
          "load-run.sizes.tiny.drain-at-most", "PT1M",
          "loan-approval.payout-run-every", "PT5S");

    }

  }

  @Inject
  LoadRun loadRun;

  @Test
  public void aTinyRunStartsAndFinishes() {

    final var report = loadRun.run("tiny");

    assertThat(report.finished())
        .describedAs("Every workflow of the run ended. The report:%n%s", report)
        .isTrue();
    assertThat(report.failures())
        .describedAs("No request and no decision failed. The report:%n%s", report)
        .isZero();
    // warm-up, steady, one step, the peak and the drain
    assertThat(report.stages()).hasSize(5);

  }

}
