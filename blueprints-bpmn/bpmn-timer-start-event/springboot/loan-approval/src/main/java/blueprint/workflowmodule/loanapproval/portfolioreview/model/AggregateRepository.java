package blueprint.workflowmodule.loanapproval.portfolioreview.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data names a repository bean after the interface, so the second
 * {@code AggregateRepository} of a workflow module needs a name of its own.
 */
@Repository("portfolioReviewRepository")
public interface AggregateRepository extends JpaRepository<Aggregate, String> {

  /**
   * The reviews which ran for one occasion.
   *
   * @param occasion Why the reviews ran.
   * @return The reviews of that occasion.
   */
  List<Aggregate> findByOccasion(
      Occasion occasion);

}
