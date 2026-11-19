package blueprint.workflowmodule.loanapproval.overdraft.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data names a repository bean after the interface, so each further
 * {@code AggregateRepository} of a workflow module needs a name of its own.
 */
@Repository("overdraftRepository")
public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
