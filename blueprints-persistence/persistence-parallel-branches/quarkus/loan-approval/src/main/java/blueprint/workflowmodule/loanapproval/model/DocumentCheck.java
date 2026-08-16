package blueprint.workflowmodule.loanapproval.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the branch collecting the documents writes.
 *
 * <p>
 * A row of its own, for the same reason as {@link PartnerApproval}. The transaction writing
 * it belongs to VanillaBP: this branch is an ordinary service task, and its handler runs in
 * the transaction the framework opens around it.
 * </p>
 */
@Entity
@Table(name = "DOCUMENT_CHECK")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCheck {

  @Id
  @GeneratedValue
  private Long id;

  /** How many documents the document service handed over. */
  @Column
  private Integer documentsReceived;

  /** When the documents were collected. */
  @Column
  private OffsetDateTime collectedAt;

}
