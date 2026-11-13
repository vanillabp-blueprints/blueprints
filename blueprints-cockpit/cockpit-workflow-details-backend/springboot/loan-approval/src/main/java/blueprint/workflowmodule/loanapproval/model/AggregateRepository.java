package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the workflow aggregates. The case page knows the business id of its case,
 * which is the id of the aggregate, so {@code findById} is all it needs.
 */
public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
