package blueprint.workflowmodule.ratewatch.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data names a repository bean after the interface, so the second
 * {@code AggregateRepository} of a workflow module needs a name of its own.
 */
@Repository("rateWatchRepository")
public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
