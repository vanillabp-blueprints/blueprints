package blueprint.workflowmodule.loanapproval.audit;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One change, as the auditing knows it: a number, the moment it happened and the person
 * it came from.
 *
 * <p>
 * Envers writes a row of this kind per transaction which touched an audited entity, and
 * every audit row points at one of them. The number is what names a state of the loan
 * approval, and it is the value the compliance notice carries.
 * </p>
 *
 * <p>
 * Without a class like this, Envers writes a table of its own called {@code REVINFO} which
 * holds the number and the moment and nothing else. The annotation names the listener
 * filling the column this application adds, and both together are all it takes to replace
 * the default.
 * </p>
 */
@Entity
@Table(name = "LOAN_APPROVAL_REVISION")
@RevisionEntity(ChangeAuthor.class)
@Getter
@Setter
public class AuditedChange {

  /** The number of the change, which every audit row of this transaction points at. */
  @Id
  @GeneratedValue
  @RevisionNumber
  @Column(name = "ID")
  private int id;

  /** When it happened, in milliseconds since the epoch, written by Envers. */
  @RevisionTimestamp
  @Column(name = "CHANGED_AT")
  private long changedAt;

  /** Who made the change, as {@link ChangeAuthor} knew it while it was made. */
  @Column(name = "CHANGED_BY")
  private String changedBy;

}
