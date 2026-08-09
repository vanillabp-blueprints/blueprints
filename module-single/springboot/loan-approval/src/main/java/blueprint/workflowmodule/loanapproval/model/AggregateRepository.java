package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A plain Spring Data repository. VanillaBP uses it to load and save the workflow
 * aggregate, which is why no other registration is necessary.
 */
public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
