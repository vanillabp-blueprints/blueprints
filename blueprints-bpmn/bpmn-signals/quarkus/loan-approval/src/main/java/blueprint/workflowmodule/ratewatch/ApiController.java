package blueprint.workflowmodule.ratewatch;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the rate watch. Two GET requests: start one, look at it.
 *
 * <p>
 * There is nothing here that publishes a rate. This use case only listens, and the URL that
 * makes it continue belongs to the loan approval.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Path("/api/rate-watch")
public class ApiController {

  @Inject
  Service service;

  /**
   * Starts a rate watch.
   *
   * @return The id of the watch started.
   */
  @GET
  @Path("/start")
  public String start() {

    final var watchId = UUID.randomUUID().toString();

    service.startWatching(watchId);

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
  @GET
  @Path("/{watchId}")
  public String show(
      @PathParam("watchId") final String watchId) {

    return service
        .getRateWatch(watchId)
        .map(Object::toString)
        .orElse("unknown rate watch '"
            + watchId
            + "'");

  }

}
