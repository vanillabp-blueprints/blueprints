package blueprint.workflowmodule.loanapproval.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.integration.spi.AggregatePersistenceAware;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The persistence of the workflow aggregate, written by the application. The second of the four
 * beans, and the one VanillaBP cannot do without: it has to save the aggregate when a workflow
 * starts, load it before a {@code @WorkflowTask} method and save it afterwards.
 *
 * <p>
 * A map stands in for the real thing here. In an application this is an event store, a ledger, a
 * message producer or a service behind an API - anything the platform does not manage, which is
 * why this interface exists at all. The methods are the whole contract, and every one of them is
 * needed for a reason:
 * </p>
 *
 * <ul>
 * <li>{@code getAggregateClass} selects this implementation for that aggregate. The most
 * specific implementation wins, so one class may serve a whole family through a shared
 * interface.</li>
 * <li>{@code save} and {@code loadById} are what VanillaBP calls, always inside the unit of work
 * it opened through {@link UnitOfWork}.</li>
 * <li>{@code getAggregateId} is the identity of the business case, used as the workflow's
 * business key.</li>
 * <li>{@code getAggregateIdName} is needed by an adapter which stores the id in the BPMS itself,
 * naming a process variable after the id property. Camunda 8 does, so leaving it out would fail
 * while a workflow is started - with a message saying exactly that.</li>
 * <li>{@code getAggregateIdType} lets VanillaBP validate at startup that the id survives the
 * outbox, where it is kept as text.</li>
 * </ul>
 *
 * <p>
 * <strong>The store enlists in the unit of work</strong>, and that is the part which is easy to
 * get wrong: a write goes into a buffer of the running unit of work first and into the store
 * itself when that unit of work commits. Writing straight into the map would make the aggregate
 * of an aborted business case visible, and VanillaBP's promise that a workflow and its aggregate
 * cannot exist without each other would be void. A durable store enlists in whatever its
 * technology offers instead of copying this buffer.
 * </p>
 *
 * <p>
 * Copies go in and out on purpose: a store which handed out its own instances would let a caller
 * change what is stored without saving.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#aggregate-persistence">Aggregate
 *      persistence</a>
 */
@ApplicationScoped
public class AggregateStore implements AggregatePersistenceAware<Aggregate> {

  @Inject
  UnitOfWork unitOfWork;

  /** What is stored. Written when a unit of work commits, never before. */
  private final Map<String, Aggregate> loanApprovals = new ConcurrentHashMap<>();

  /** What the unit of work running on this thread has written so far. */
  private final ThreadLocal<Map<String, Aggregate>> uncommitted = ThreadLocal.withInitial(LinkedHashMap::new);

  @Override
  public Class<Aggregate> getAggregateClass() {

    return Aggregate.class;

  }

  @Override
  public Object getAggregateId(
      final Aggregate loanApproval) {

    return loanApproval.getLoanRequestId();

  }

  @Override
  public String getAggregateIdName() {

    return "loanRequestId";

  }

  @Override
  public Class<?> getAggregateIdType() {

    return String.class;

  }

  @Override
  public Aggregate save(
      final Aggregate loanApproval) {

    if (!unitOfWork.isTransactionActive()) {
      loanApprovals.put(loanApproval.getLoanRequestId(), copyOf(loanApproval));
      return loanApproval;
    }

    if (uncommitted
        .get()
        .isEmpty()) {
      enlist();
    }
    uncommitted
        .get()
        .put(loanApproval.getLoanRequestId(), copyOf(loanApproval));
    return loanApproval;

  }

  @Override
  public Aggregate loadById(
      final Object loanRequestId) {

    return find((String) loanRequestId).orElse(null);

  }

  /**
   * Reading for the application itself, which is why it answers an {@link Optional} instead of
   * {@code null}: the API asks whether a loan approval exists.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, as the running unit of work sees it.
   */
  public Optional<Aggregate> find(
      final String loanRequestId) {

    return Optional
        .ofNullable(
            uncommitted
                .get()
                .getOrDefault(loanRequestId, loanApprovals.get(loanRequestId)))
        .map(AggregateStore::copyOf);

  }

  /**
   * Makes what this unit of work wrote visible when it commits, and forgets it when it does
   * not. Registered once per unit of work, on the first write.
   */
  private void enlist() {

    unitOfWork.afterCommit(() -> {
      loanApprovals.putAll(uncommitted.get());
      uncommitted
          .get()
          .clear();
    });
    unitOfWork.afterRollback(
        () -> uncommitted
            .get()
            .clear());

  }

  private static Aggregate copyOf(
      final Aggregate loanApproval) {

    return Aggregate
        .builder()
        .loanRequestId(loanApproval.getLoanRequestId())
        .amount(loanApproval.getAmount())
        .creditRating(loanApproval.getCreditRating())
        .build();

  }

}
