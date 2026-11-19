package blueprint.workflowmodule.loanapproval.portfolioreview.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate of a review which the BPMS started on its own.
 *
 * <p>
 * The application builds this object in a {@code @WorkflowStartedByBpms} method. There are two
 * such methods, one per start event, and each one builds the aggregate in its own way.
 * {@link #occasion} shows which one did.
 * </p>
 *
 * <p>
 * The entity has a name of its own. Two JPA entities called {@code Aggregate} in one
 * persistence unit would clash, and the reference structure gives every use case a class of
 * that name.
 * </p>
 *
 * <p>
 * Nothing of this class reaches the BPMS: {@code @NoSyncWithBPMS} and no attribute with
 * {@code @SyncWithBPMS}. No expression in the model reads the aggregate. A timer on a start
 * event could not read it anyway, because the aggregate does not exist before the timer fires.
 * </p>
 */
@Entity(name = "PortfolioReview")
@Table(name = "PORTFOLIO_REVIEW")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The ID of the review, and with it the ID of the workflow. The application picks it in
   * {@code @WorkflowStartedByBpms}, and the BPMS is told about it afterwards.
   */
  @Id
  private String reviewId;

  /** Why the review runs. The method which built the aggregate sets it. */
  @Column
  @Enumerated(EnumType.STRING)
  private Occasion occasion;

  /** The name of the signal, for a review somebody asked for. Empty for a scheduled one. */
  @Column
  private String signalName;

  /** How many loan approvals the review found, written by the service task. */
  @Column
  private Integer approvalsReviewed;

}
