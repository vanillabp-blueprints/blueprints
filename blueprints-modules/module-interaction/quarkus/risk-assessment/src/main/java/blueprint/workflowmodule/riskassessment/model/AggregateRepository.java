package blueprint.workflowmodule.riskassessment.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing this module's workflow aggregate, for the application and for
 * VanillaBP.
 *
 * <p>
 * One repository per aggregate, and VanillaBP picks the one matching the aggregate it is
 * about to read or write. Two modules with a repository each therefore need no configuration
 * to keep them apart.
 * </p>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {
}
