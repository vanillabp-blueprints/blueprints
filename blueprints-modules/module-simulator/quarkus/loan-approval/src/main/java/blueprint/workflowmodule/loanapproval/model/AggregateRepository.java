package blueprint.workflowmodule.loanapproval.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {
}
