package blueprint.workflowmodule.loanapproval.portfolioreview;

import java.util.stream.Collectors;

import blueprint.workflowmodule.loanapproval.portfolioreview.api.PortfolioReviewApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the review. There is no endpoint starting one, because no endpoint could: the
 * timer starts reviews on its own, and the signal only asks.
 *
 * <p>
 * The paths, the parameters and the answers come from {@link PortfolioReviewApi}, the interface the
 * build generates from {@code src/main/resources/loan-approval/openapi/portfolio-review.yaml}. This
 * class only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements PortfolioReviewApi {

  @Inject
  Service portfolioReview;

  /**
   * Asks for a review by broadcasting the signal.
   *
   * @return What was done, for the browser to show.
   */
  @Override
  public String request() {

    portfolioReview.request();

    log.info(
        "Show the reviews -> http://localhost:8080/loan-approval/api/portfolio-reviews");

    return "A review was requested. Whether one starts is the engine's decision,"
        + " and the reviews are listed at /loan-approval/api/portfolio-reviews";

  }

  /**
   * Lists the reviews which ran, whoever started them.
   *
   * @return Every review this application has seen.
   */
  @Override
  public String listReviews() {

    final var reviews = portfolioReview.getAll();

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
   * @param reviewId The ID the application picked while it built the aggregate.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String reviewId) {

    return portfolioReview
        .get(reviewId)
        .map(Object::toString)
        .orElse("unknown review '"
            + reviewId
            + "'");

  }

}
