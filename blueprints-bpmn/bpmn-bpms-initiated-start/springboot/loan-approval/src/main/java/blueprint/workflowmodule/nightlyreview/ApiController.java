package blueprint.workflowmodule.nightlyreview;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * The API of the review. There is no endpoint starting one, because no endpoint could: the
 * timer starts reviews on its own, and the signal only asks.
 */
@Slf4j
@RestController("nightlyReviewApiController")
@RequestMapping("/api/nightly-review")
public class ApiController {

  @Autowired
  private Service service;

  /**
   * Asks for a review by broadcasting the signal.
   *
   * @return What was done, for the browser to show.
   */
  @GetMapping("/request")
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
  @GetMapping
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
  @GetMapping("/{reviewId}")
  public String show(
      @PathVariable final String reviewId) {

    return service
        .getReview(reviewId)
        .map(Object::toString)
        .orElse("unknown review '"
            + reviewId
            + "'");

  }

}
