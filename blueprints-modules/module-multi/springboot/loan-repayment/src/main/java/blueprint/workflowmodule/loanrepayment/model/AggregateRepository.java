package blueprint.workflowmodule.loanrepayment.model;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The repository of this module's workflow aggregate. VanillaBP uses it to load and save
 * the aggregate, and the module's own business code uses it to read.
 *
 * <p>
 * It is found because the module's auto-configuration enables the repositories of its own
 * package, not because the application scans for them.
 * </p>
 */
public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
