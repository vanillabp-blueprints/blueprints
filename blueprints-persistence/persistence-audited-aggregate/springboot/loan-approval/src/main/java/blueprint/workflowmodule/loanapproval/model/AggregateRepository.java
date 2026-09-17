package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.history.RevisionRepository;

/**
 * Loading and storing the workflow aggregate, and reading what it looked like before.
 *
 * <p>
 * {@link JpaRepository} is the part every blueprint has. {@link RevisionRepository}
 * comes from Spring Data Envers and reads the audit table: {@code findRevisions} answers
 * the trail of a loan approval, {@code findRevision} one state out of it. Its third type
 * parameter is the type of the revision number, {@code Integer} here because the
 * revision entity extends Envers' {@code DefaultRevisionEntity}.
 * </p>
 *
 * <p>
 * A revision repository needs a repository factory of its own, which is what
 * {@code @EnableEnversRepositories} in
 * {@link blueprint.workflowmodule.loanapproval.config.AuditedRepositories} switches on.
 * </p>
 */
public interface AggregateRepository extends JpaRepository<Aggregate, String>, RevisionRepository<Aggregate, String, Integer> {
}
