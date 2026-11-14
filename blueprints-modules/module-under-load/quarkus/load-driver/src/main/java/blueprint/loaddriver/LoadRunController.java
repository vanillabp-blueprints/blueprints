package blueprint.loaddriver;

import blueprint.loaddriver.api.LoadRunApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Starts a load run and shows its report, in a browser.
 *
 * <p>
 * The paths and the parameters come from {@link LoadRunApi}, the interface the build generates
 * from {@code src/main/resources/openapi/load-run.yaml}. This class only implements it.
 * </p>
 */
@ApplicationScoped
public class LoadRunController implements LoadRunApi {

  @Inject
  LoadRun loadRun;

  /**
   * Starts a run in the background.
   *
   * @param size The name of the run size.
   * @return Whether the run started, and where its report is.
   */
  @Override
  public String start(
      final String size) {

    return loadRun.start(size);

  }

  /**
   * @return The report of the last run, or how far the current run has come.
   */
  @Override
  public String report() {

    return loadRun.report();

  }

}
