package blueprint.workflowmodule.loanapproval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the first section of the process found out: the documents the customer sent in.
 *
 * <p>
 * One object per section keeps a growing aggregate readable. It is also the reason a
 * section needs care: the object does not exist before the section runs, so every getter of
 * {@link Aggregate} which reads into it has to survive a {@code null} here.
 * </p>
 *
 * <p>
 * The values below stay in the application. The BPMS never sees them, because
 * {@link Aggregate} is annotated {@code @NoSyncWithBPMS} and only its own boolean getters
 * are shared. So their types are a question of the data model alone.
 * </p>
 *
 * <p>
 * It is embedded rather than a table of its own, which is what makes it {@code null} when
 * the section has not run: all of its columns are empty then, and that is how the
 * persistence layer reads an embedded object back.
 * </p>
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCheck {

  /** How many documents this application needs. Written by the first task of the section. */
  @Column(name = "DOCUMENTS_REQUIRED")
  private Integer documentsRequired;

  /** How many documents arrived. Written by the first task as well. */
  @Column(name = "DOCUMENTS_RECEIVED")
  private Integer documentsReceived;

  /** Whether every document carried the signature it needs. Written by the second task. */
  @Column(name = "SIGNATURES_COMPLETE")
  private Boolean signaturesComplete;

}
