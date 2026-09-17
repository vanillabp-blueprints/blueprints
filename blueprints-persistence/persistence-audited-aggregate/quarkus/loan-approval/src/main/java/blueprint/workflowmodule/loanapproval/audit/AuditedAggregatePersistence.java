package blueprint.workflowmodule.loanapproval.audit;

import java.util.List;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.integration.spi.AggregatePersistenceAware;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * How this application stores its workflow aggregate, and how it reads a state the
 * aggregate had before. The second half is what this blueprint exists for.
 *
 * <p>
 * VanillaBP finds the repository of an aggregate by itself, so most blueprints have no
 * class like this. An application which audits needs one, because two of the questions
 * VanillaBP may ask can only be answered by the auditing: which state the aggregate
 * stands at, and what it looked like at a state somebody names. The rest of the methods
 * is the repository, spelled out.
 * </p>
 *
 * <p>
 * Both auditing methods have a default in the interface, and both defaults are the
 * behaviour of an application without an auditing: no state is named, and every load
 * reads the current one. So overriding them is the whole step from "not audited" to
 * "audited", and nothing else in the application has to know.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#aggregate-persistence">Aggregate
 *      persistence</a>
 */
@ApplicationScoped
public class AuditedAggregatePersistence implements AggregatePersistenceAware<Aggregate> {

  @Inject
  AggregateRepository loanApprovals;

  /** The entity manager Envers is asked through. */
  @Inject
  EntityManager entityManager;

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
  public Aggregate save(
      final Aggregate loanApproval) {

    if (entityManager.contains(loanApproval)) {
      return loanApproval;
    }
    return entityManager.merge(loanApproval);

  }

  @Override
  public Aggregate loadById(
      final Object loanRequestId) {

    return loanApprovals
        .findByIdOptional(String.valueOf(loanRequestId))
        .orElse(null);

  }

  /**
   * The id of the change this transaction is making, which is how a caller names the
   * state it is looking at.
   *
   * <p>
   * The revision itself has no number yet. Envers writes the revision row while the
   * transaction commits, and an outbox entry is written before that, so an entry which
   * named the number would name something nobody has. The application therefore makes up
   * an id of its own, {@link ChangeBeingMade}, and the revision of this transaction is
   * written with that id in it. The id exists immediately, it belongs to the whole
   * transaction rather than to a single write, and reading it back is a query.
   * </p>
   *
   * <p>
   * The other way round works too, for an application which uses optimistic locking: the
   * version attribute of the aggregate names a state as well. It is assigned per write
   * though, so a transaction which writes twice names a state its audit row does not
   * carry - see the README.
   * </p>
   *
   * @param loanApproval The aggregate whose current state is to be named.
   * @return The id of the change being made.
   */
  @Override
  public String getAuditingId(
      final Aggregate loanApproval) {

    return ChangeBeingMade.id();

  }

  /**
   * The loan approval as it was at that change, detached: a past state is something to
   * read, never something to write back.
   *
   * <p>
   * Two steps, because the id is the application's and the audit rows are Envers'. The
   * revision carrying the id is looked up first, and its number is what Envers is then
   * asked for.
   * </p>
   *
   * <p>
   * Answering {@code null} says the state is gone, which happens once the auditing was
   * cleaned up while the outbox entry was waiting. VanillaBP then reads the current state
   * and warns, naming the aggregate and the id, because a report with newer values is
   * better than no report at all.
   * </p>
   *
   * @param loanRequestId The id of the loan approval.
   * @param auditingId    The change to read, as {@link #getAuditingId(Aggregate)} named
   *                      it.
   * @return The loan approval as it was, or {@code null} if that change is gone.
   */
  @Override
  public Aggregate loadByIdAndAuditingId(
      final Object loanRequestId,
      final String auditingId) {

    final var revisions = entityManager
        .createQuery(
            "select change.id from AuditedChange change where change.changeId = :changeId",
            Integer.class)
        .setParameter("changeId", auditingId)
        .getResultList();

    if (revisions.isEmpty()) {
      return null;
    }

    return AuditReaderFactory
        .get(entityManager)
        .find(Aggregate.class, String.valueOf(loanRequestId), revisions.getFirst());

  }

  /**
   * The trail of a loan approval: every state it went through, with the number of the
   * change and the person who made it.
   *
   * @param loanRequestId The id of the loan approval.
   * @return The revisions, oldest first.
   */
  public List<String> trailOf(
      final String loanRequestId) {

    final List<?> rows = AuditReaderFactory
        .get(entityManager)
        .createQuery()
        .forRevisionsOfEntity(Aggregate.class, false, true)
        .add(AuditEntity.id().eq(loanRequestId))
        .addOrder(AuditEntity.revisionNumber().asc())
        .getResultList();

    return rows
        .stream()
        .map(Object[].class::cast)
        .map(row -> {
          final var loanApproval = (Aggregate) row[0];
          final var change = (AuditedChange) row[1];
          return "#%s by %s: %s".formatted(change.getId(), change.getChangedBy(), loanApproval);
        })
        .toList();

  }

}
