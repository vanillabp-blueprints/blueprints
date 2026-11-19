package blueprint.workflowmodule.loanapproval.portfolioreview.model;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate of the portfolio review, for the application
 * and for VanillaBP. The second repository of this workflow module needs no name of its
 * own: a bean is found by its type here, and the two types differ.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {

  /**
   * The reviews which ran for one occasion.
   *
   * @param occasion Why the reviews ran.
   * @return The reviews of that occasion.
   */
  public List<Aggregate> findByOccasion(
      final Occasion occasion) {

    return list("occasion", occasion);

  }

}
