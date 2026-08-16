package blueprint.workflowmodule.nightlyreview.model;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate of the nightly review, for the application
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
   * The reviews a given kind of start event produced.
   *
   * @param startedBy The kind of trigger, as {@code BpmsStartTrigger.Kind} names it.
   * @return The reviews started that way.
   */
  public List<Aggregate> findByStartedBy(
      final String startedBy) {

    return list("startedBy", startedBy);

  }

}
