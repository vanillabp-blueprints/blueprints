package blueprint.workflowmodule.loanapproval.model;

import java.time.Instant;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the
 * process needs to know. There are no process variables - this is the single source of
 * truth, and it stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * The last three attributes are written when the workflow ended. That the business case is
 * closed is a fact of the business case, not of the BPMS, so it belongs here rather than in
 * a query against an engine.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so nothing reaches the BPMS unless it
 * says otherwise. One getter says otherwise: the gateway reads
 * {@link #isRatedAcceptable()}, so that answer carries {@code @SyncWithBPMS}. Everything
 * else stays in the application, the attributes written at the end included, because no
 * expression in the model reads them. What the BPMS holds besides the answer is the
 * aggregate's ID, which VanillaBP always shares because that is how it finds the workflow
 * again.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated
   * one makes a workflow started twice for the same business case a detectable
   * duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** Filled by the business code the first service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * What the rating counts as, decided by the business code and read by the gateway. The
   * threshold itself lives in the module's configuration, so moving it is not a new process
   * version - see the blueprint <code>bpmn-gateways</code>.
   */
  @Column
  private String ratingBand;

  /** Written by the task in front of one of the two end events. */
  @Column
  private Boolean customerInformed;

  /** When the workflow ended, taken from what the BPMS reported. */
  @Column
  private Instant closedAt;

  /**
   * How it ended, as far as the BPMS can tell: <code>COMPLETED</code> for a workflow which
   * reached an end event, <code>TERMINATED</code> for one ended without reaching one.
   */
  @Column
  private String closedBy;

  /**
   * Which end event was reached, where the BPMS reports it. It stays <code>null</code> on a
   * BPMS which does not, which is why no business decision hangs on it.
   */
  @Column
  private String closedAtEndEvent;

  /**
   * The question the gateway asks. It is a getter rather than an attribute, so the model
   * knows the decision and nothing about the data behind it.
   *
   * <p>
   * Annotated {@code @SyncWithBPMS} because the BPMS evaluates the gateway against what
   * VanillaBP shared with it. It is the only value of this class which is shared.
   * </p>
   *
   * @return Whether the rating is good enough.
   */
  @SyncWithBPMS
  public boolean isRatedAcceptable() {

    return "acceptable".equals(ratingBand);

  }

}
