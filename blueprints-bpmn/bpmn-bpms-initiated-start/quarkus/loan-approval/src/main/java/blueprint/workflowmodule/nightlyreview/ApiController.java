package blueprint.workflowmodule.nightlyreview;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the review. There is no endpoint starting one, because no endpoint could: the
 * timer starts reviews on its own, and the signal only asks.
 */
@Slf4j
@ApplicationScoped
@Path("/api/nightly-review")
public class ApiController {

  @Inject
  Service service;

  /**
   * Asks for a review by broadcasting the signal.
   *
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/request")
  public String request() {

    service.requestReview();

    log.info(
        "Show the reviews -> http://localhost:8080/api/nightly-review");

    return "A review was requested. Whether one starts is the engine's decision,"
        + " and the reviews are listed at /api/nightly-review";

  }

  /**
   * Lists the reviews that ran, whoever started them.
   *
   * @return Every review this application has seen.
   */
  @GET
  public String list() {

    final var reviews = service.getReviews();

    if (reviews.isEmpty()) {
      return "No review ran yet. The timer needs a moment after the application started.";
    }

    return reviews
        .stream()
        .map(Object::toString)
        .collect(Collectors.joining("\n"));

  }

  /**
   * Shows one review.
   *
   * @param reviewId The ID VanillaBP assigned.
   * @return The workflow aggregate as it is stored right now.
   */
  @GET
  @Path("/{reviewId}")
  public String show(
      @PathParam("reviewId") final String reviewId) {

    return service
        .getReview(reviewId)
        .map(Object::toString)
        .orElse("unknown review '"
            + reviewId
            + "'");

  }

}
