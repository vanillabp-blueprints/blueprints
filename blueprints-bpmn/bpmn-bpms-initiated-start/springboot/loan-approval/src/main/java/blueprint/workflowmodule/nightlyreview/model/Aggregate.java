package blueprint.workflowmodule.nightlyreview.model;

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
 * The application builds this object, exactly as it builds the aggregate of a workflow it
 * starts itself. Nobody called for the workflow, but the aggregate is still the
 * application's: {@code @WorkflowStartedByBpms} returns one, and VanillaBP saves it before
 * anything else of the process runs.
 * </p>
 *
 * <p>
 * The entity is given a name of its own. Two JPA entities called {@code Aggregate} in one
 * persistence unit would clash, and the reference structure gives every use case a class of
 * that name.
 * </p>
 *
 * <p>
 * Nothing of this class reaches the BPMS: {@code @NoSyncWithBPMS} and no attribute with
 * {@code @SyncWithBPMS}. No expression in the model reads the aggregate, and the two start
 * events bring no variable in. What the trigger carried arrives as a
 * {@code BpmsStartTrigger} argument of the {@code @WorkflowStartedByBpms} method, and the
 * moment of the review arrives as a process variable the model filled.
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
   * The ID of the review, and with it the ID of the workflow. The application picks it in
   * {@code @WorkflowStartedByBpms} and the BPMS is told about it afterwards. Nobody else
   * names a workflow, whoever started it.
   */
  @Id
  private String reviewId;

  /** Which kind of start event fired, taken from the trigger. */
  @Column
  private String startedBy;

  /** The BPMN id of the start event, taken from the trigger. */
  @Column
  private String startEventId;

  /**
   * When the review looked at the approvals, as the model's expression wrote it.
   *
   * <p>
   * A text and not an {@code Instant}, because each expression language writes a moment in
   * its own form: Camunda 7 hands the ISO text of an instant, Camunda 8 hands what FEEL
   * makes of {@code now()}. Reading it as a text is what lets one workflow service serve
   * both, and it is the honest form for a value the model owns.
   * </p>
   */
  @Column
  private String reviewedAt;

  /** How many loan approvals the review found, written by the service task. */
  @Column
  private Integer approvalsReviewed;

}
