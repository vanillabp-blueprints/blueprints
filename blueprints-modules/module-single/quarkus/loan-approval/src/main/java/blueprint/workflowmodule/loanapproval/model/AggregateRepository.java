package blueprint.workflowmodule.loanapproval.model;

import java.util.Optional;

import io.vanillabp.integration.spi.AggregatePersistenceAware;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP.
 *
 * <p>
 * VanillaBP has to read and write the aggregate itself: it stores it when a workflow is
 * started, and it loads it before a {@code @WorkflowTask} method runs and saves it
 * afterwards. How that is done is the application's decision, since there is no single
 * persistence idiom to assume - so the application says it, by implementing
 * {@link AggregatePersistenceAware} for its aggregate class. The implementation whose
 * generic parameter fits an aggregate best is the one used for it.
 * </p>
 *
 * <p>
 * {@code @Transactional} joins the transaction of whoever calls in, which is what makes
 * the aggregate and the state of the BPMS commit together. It opens one only where there
 * is none: VanillaBP loads the aggregate from a thread of its own when it completes the
 * start of a workflow in a remote BPMS, and an entity cannot be read outside a
 * transaction.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
@Transactional
public class AggregateRepository implements AggregatePersistenceAware<Aggregate> {

  @Inject
  EntityManager entityManager;

  /**
   * The loan approval of a loan request, for the application's own reads.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> findById(
      final String loanRequestId) {

    return Optional.ofNullable(entityManager.find(Aggregate.class, loanRequestId));

  }

  @Override
  public Class<Aggregate> getAggregateClass() {

    return Aggregate.class;

  }

  @Override
  public Aggregate save(
      final Aggregate loanApproval) {

    // The id is a natural one and is set before saving, so one call covers both the first
    // save of a loan approval and every later one.
    return entityManager.merge(loanApproval);

  }

  @Override
  public Object getAggregateId(
      final Aggregate loanApproval) {

    return loanApproval.getLoanRequestId();

  }

  @Override
  public String getAggregateIdName() {

    // Asked for by adapters which keep the aggregate's id in the BPMS itself, where it
    // becomes a process variable of this name. An adapter having a business key of its own
    // never asks.
    return "loanRequestId";

  }

  @Override
  public Aggregate loadById(
      final Object loanRequestId) {

    return entityManager.find(Aggregate.class, loanRequestId);

  }

}
