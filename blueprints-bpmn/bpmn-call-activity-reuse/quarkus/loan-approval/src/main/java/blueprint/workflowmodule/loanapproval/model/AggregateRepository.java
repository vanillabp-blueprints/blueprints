package blueprint.workflowmodule.loanapproval.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP.
 * A bean is found by its type here, and the three repositories of this module have three
 * different types, so none of them needs a name of its own.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {
}
