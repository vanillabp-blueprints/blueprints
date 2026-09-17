package blueprint.workflowmodule.loanapproval.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP.
 *
 * <p>
 * The trail of an audited aggregate is not read here. Hibernate Envers has a query API of
 * its own, and a repository of this kind does not know it, so the two audit questions and
 * the trail are answered together in
 * {@link blueprint.workflowmodule.loanapproval.audit.AuditedAggregatePersistence}.
 * </p>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {
}
