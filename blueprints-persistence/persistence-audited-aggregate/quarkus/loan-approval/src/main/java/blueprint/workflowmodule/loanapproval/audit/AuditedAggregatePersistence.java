package blueprint.workflowmodule.loanapproval.audit;

import java.util.List;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.exception.RevisionDoesNotExistException;
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
   * The revision the changes of the running transaction will belong to.
   *
   * <p>
   * Envers hands out a revision when the transaction is flushed, and an outbox entry is
   * written before that, so an entry which wants to name the state it saw would name a
   * revision which does not exist yet. The second argument of
   * {@code getCurrentRevision} is what solves it: it writes the revision row right here,
   * and every change of this transaction is then recorded under exactly that number.
   * </p>
   *
   * <p>
   * The other way round works too, for an application which uses optimistic locking: the
   * version attribute of the aggregate names a state as well, and it is there without
   * asking anybody. This blueprint takes the revision because it asks nothing of the
   * application - see the README.
   * </p>
   *
   * <p>
   * {@code getCurrentRevision} carries a deprecation which points at
   * {@code RevisionListener}, and that pointer does not lead anywhere for this question: a
   * listener is called while the transaction commits, which is after the notice was
   * written. Envers has announced a replacement since version 5.2 and has not shipped one,
   * the method works, and no other API answers the number early. So it is used here, with
   * this paragraph instead of a warning in every build.
   * </p>
   *
   * @param loanApproval The aggregate whose current state is to be named.
   * @return The revision as text, because an outbox entry carries text.
   */
  @Override
  @SuppressWarnings("deprecation")
  public String getAuditingId(
      final Aggregate loanApproval) {

    final var revision = AuditReaderFactory
        .get(entityManager)
        .getCurrentRevision(AuditedChange.class, true);

    return String.valueOf(revision.getId());

  }

  /**
   * The loan approval as it was at that revision, detached: a past state is something to
   * read, never something to write back.
   *
   * <p>
   * Answering {@code null} says the state is gone, which happens once the auditing was
   * cleaned up while the outbox entry was waiting. VanillaBP then reads the current state
   * and warns, naming the aggregate and the revision, because a report with newer values
   * is better than no report at all.
   * </p>
   *
   * @param loanRequestId The id of the loan approval.
   * @param auditingId    The revision to read, as {@link #getAuditingId(Aggregate)}
   *                      wrote it.
   * @return The loan approval as it was, or {@code null} if that revision is gone.
   */
  @Override
  public Aggregate loadByIdAndAuditingId(
      final Object loanRequestId,
      final String auditingId) {

    final var auditReader = AuditReaderFactory.get(entityManager);
    final var revision = Integer.valueOf(auditingId);

    try {
      // Envers answers the newest state up to the revision asked for, so a revision
      // which is gone would silently be answered with an older one. Asking for the
      // revision itself is what tells "cleaned up" apart from "nothing changed since".
      auditReader.findRevision(AuditedChange.class, revision);
    } catch (RevisionDoesNotExistException e) {
      return null;
    }

    return auditReader.find(Aggregate.class, String.valueOf(loanRequestId), revision);

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
