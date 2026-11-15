package blueprint.workflowmodule.nightlyreview;

import java.util.stream.Collectors;

import blueprint.workflowmodule.nightlyreview.api.NightlyReviewApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the review. There is no endpoint starting one, because no endpoint could: the
 * timer starts reviews on its own, and the signal only asks.
 *
 * <p>
 * The paths, the parameters and the answers come from {@link NightlyReviewApi}, the interface
 * the build generates from {@code src/main/resources/openapi/nightly-review.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements NightlyReviewApi {

  @Inject
  Service nightlyReview;

  /**
   * Asks for a review by broadcasting the signal.
   *
   * @return What was done, for the browser to show.
   */
  @Override
  public String request() {

    nightlyReview.request();

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
  @Override
  public String listReviews() {

    final var reviews = nightlyReview.getAll();

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
  @Override
  public String show(
      final String reviewId) {

    return nightlyReview
        .get(reviewId)
        .map(Object::toString)
        .orElse("unknown review '"
            + reviewId
            + "'");

  }

}
