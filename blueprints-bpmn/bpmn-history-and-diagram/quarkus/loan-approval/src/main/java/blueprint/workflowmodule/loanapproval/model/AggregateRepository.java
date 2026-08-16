package blueprint.workflowmodule.loanapproval.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP.
 *
 * <p>
 * A repository is all it takes. VanillaBP has to read and write the aggregate itself - it
 * stores it when a workflow is started, and it loads it before a {@code @WorkflowTask}
 * method runs and saves it afterwards - and it recognises the repository of an aggregate,
 * so no application code says how that is done. An application whose persistence does not
 * fit any of the known patterns implements
 * {@code io.vanillabp.integration.spi.AggregatePersistenceAware} instead, and that
 * implementation always wins.
 * </p>
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
