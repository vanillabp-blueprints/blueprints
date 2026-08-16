package blueprint.workflowmodule.ratewatch.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Loading and storing the workflow aggregate of the rate watch, for the application and
 * for VanillaBP. The second repository of this workflow module needs no name of its own: a
 * bean is found by its type here, and the two types differ.
 *
 * <p>
 * {@code @Transactional} joins the transaction of whoever calls in, which is what makes the
 * aggregate and the state of the BPMS commit together. It opens one only where there is
 * none: VanillaBP loads the aggregate from a thread of its own when it completes the start
 * of a workflow in a remote BPMS, and an entity cannot be read outside a transaction. The
 * annotation goes away once VanillaBP brings the transaction along.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
@Transactional
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {
}
