package blueprint.workflowmodule.loanapproval.model;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP.
 *
 * <p>
 * A repository is all it takes, and it is the same story as in the base blueprint with a
 * different technology: VanillaBP has to read and write the aggregate itself, it recognises
 * the repository of an aggregate, and no application code says how that is done.
 * </p>
 *
 * <p>
 * MongoDB Panache binds a MongoDB transaction to the platform's transaction, and since the
 * framework's own MongoDB stores write through that same session, the aggregate, the entry
 * scheduling the second phase of a start and the record about a delivered task share one
 * transaction. Nothing here says so - it is what declaring this repository buys.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheMongoRepositoryBase<Aggregate, String> {
}
