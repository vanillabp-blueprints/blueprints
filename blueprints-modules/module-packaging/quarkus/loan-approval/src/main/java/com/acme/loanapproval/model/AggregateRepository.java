package com.acme.loanapproval.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

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
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {
}
