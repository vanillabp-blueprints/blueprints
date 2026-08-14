package blueprint.workflowmodule.nightlyreview.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data names a repository bean after the interface, so the second
 * {@code AggregateRepository} of a workflow module needs a name of its own.
 */
@Repository("nightlyReviewRepository")
public interface AggregateRepository extends JpaRepository<Aggregate, String> {

  /**
   * The reviews a given kind of start event produced.
   *
   * @param startedBy The kind of trigger, as {@code BpmsStartTrigger.Kind} names it.
   * @return The reviews started that way.
   */
  List<Aggregate> findByStartedBy(
      String startedBy);

}
