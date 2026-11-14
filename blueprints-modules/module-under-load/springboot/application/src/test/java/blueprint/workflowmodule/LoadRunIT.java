package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import blueprint.loaddriver.LoadRun;

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
@SpringBootTest(properties = {
    "load-run.sizes.tiny.stage-length=PT2S", "load-run.sizes.tiny.steps=1", "load-run.sizes.tiny.drain-at-most=PT1M",
    // The drain waits for the payout run, so it comes more often here.
    "loan-approval.payout-run-every=PT5S"
})
public class LoadRunIT {

  @Autowired
  private LoadRun loadRun;

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
