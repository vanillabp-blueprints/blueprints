package blueprint.simulator.loadrun;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import blueprint.simulator.loadrun.api.LoadRunApi;

/**
 * Starts a load run and shows its report, in a browser.
 *
 * <p>
 * The paths and the parameters come from {@link LoadRunApi}, the interface generated from
 * {@code load-run-api/load-run.yaml}. This class only implements it.
 * </p>
 */
@RestController
public class LoadRunController implements LoadRunApi {

  private final LoadRun loadRun;

  /**
   * Creates the controller.
   *
   * @param loadRun The load run it operates.
   */
  public LoadRunController(
      final LoadRun loadRun) {

    this.loadRun = loadRun;

  }

  /**
   * Starts a run in the background.
   *
   * @param size The name of the run size.
   * @return Whether the run started, and where its report is.
   */
  @Override
  public String start(
      final String size) {

    return loadRun.start(
        size,
        ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .path(PATH_REPORT)
            .toUriString());

  }

  /**
   * @return The report of the last run, or how far the current run has come.
   */
  @Override
  public String report() {

    return loadRun.report();

  }

}
