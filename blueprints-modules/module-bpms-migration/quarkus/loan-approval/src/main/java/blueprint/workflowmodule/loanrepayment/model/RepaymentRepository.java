package blueprint.workflowmodule.loanrepayment.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate of the repayment, for the application and for
 * VanillaBP.
 */
@ApplicationScoped
public class RepaymentRepository implements PanacheRepositoryBase<Repayment, String> {
}
