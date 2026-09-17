package blueprint.workflowmodule.nightlyreview.model;

import java.time.Instant;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate of a review the BPMS started on its own.
 *
 * <p>
 * Nobody built this object. VanillaBP instantiates it when the engine reports the start,
 * assigns its ID and saves it before anything else of the process runs - which is why the
 * class needs a constructor without arguments and why every attribute may be null at that
 * moment.
 * </p>
 *
 * <p>
 * The entity is given a name of its own. Two JPA entities called {@code Aggregate} in one
 * persistence unit would clash, and the reference structure gives every use case a class of
 * that name.
 * </p>
 *
 * <p>
 * Nothing of this class reaches the BPMS either way, and the class says so:
 * {@code @NoSyncWithBPMS} and no attribute with {@code @SyncWithBPMS}. No expression in
 * the model reads the aggregate, and the two start events bring no variable in. What the
 * trigger carried arrives as a {@code BpmsStartTrigger} argument of the
 * {@code @WorkflowStartedByBpms} method, not as a process variable. So the first three
 * attributes are written by the application, even though the start came from outside it.
 * </p>
 */
@Entity(name = "NightlyReview")
@Table(name = "NIGHTLY_REVIEW")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The ID VanillaBP assigned. It is the most stable identity the BPMS offers: the process
   * instance key of a remote engine, the trigger time of a timer where the engine reports
   * it, otherwise a generated value. The application never picks it here.
   */
  @Id
  private String reviewId;

  /** Which kind of start event fired, taken from the trigger. */
  @Column
  private String startedBy;

  /** When it fired, taken from the trigger. */
  @Column
  private Instant triggeredAt;

  /** The BPMN id of the start event, taken from the trigger. */
  @Column
  private String startEventId;

  /** How many loan approvals the review found, written by the service task. */
  @Column
  private Integer approvalsReviewed;

}
