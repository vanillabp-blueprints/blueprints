package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the workflow aggregates. {@code findById} is all this module needs.
 */
public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
