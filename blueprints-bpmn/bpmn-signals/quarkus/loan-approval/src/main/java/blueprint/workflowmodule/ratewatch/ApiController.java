package blueprint.workflowmodule.ratewatch;

import java.util.UUID;

import blueprint.workflowmodule.ratewatch.api.RateWatchApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the rate watch. Two GET requests: start one, look at it.
 *
 * <p>
 * There is nothing here that publishes a rate. This use case only listens, and the URL that
 * makes it continue belongs to the loan approval.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link RateWatchApi}, the interface
 * the build generates from {@code src/main/resources/openapi/rate-watch.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements RateWatchApi {

  @Inject
  Service rateWatch;

  /**
   * Starts a rate watch.
   *
   * @return The id of the watch started.
   */
  @Override
  public String start() {

    final var watchId = UUID.randomUUID().toString();

    rateWatch.startWatching(watchId);

    log.info(
        "Show the result -> http://localhost:8080/api/rate-watch/{}",
        watchId);

    return watchId;

  }

  /**
   * Shows whether the watch noticed a publication yet.
   *
   * @param watchId The id returned by starting the watch.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String watchId) {

    return rateWatch
        .get(watchId)
        .map(Object::toString)
        .orElse("unknown rate watch '"
            + watchId
            + "'");

  }

}
