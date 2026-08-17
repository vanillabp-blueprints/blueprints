package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP.
 *
 * <p>
 * A repository is all it takes, and it is the same story as in the base blueprint with a
 * different technology: VanillaBP has to read and write the aggregate itself, it recognises
 * the repository of an aggregate, and no application code says how that is done. The outbox
 * carrying the second phase of a workflow start is chosen along the same line, so declaring
 * this repository as a MongoDB one moves that store as well.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
public interface AggregateRepository extends MongoRepository<Aggregate, String> {
}
