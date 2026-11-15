package blueprint.workflowmodule.loanapproval.model;

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
 * The attribute this blueprint is about is {@link #partnerApprovalTaskId}. A task waiting
 * for a surrounding system may wait for days, so something has to remember which task the
 * answer belongs to. The aggregate is where that belongs: it is transactional, it is
 * queryable, and it survives a restart of both sides.
 * </p>
 *
 * <p>
 * The class carries {@code @NoSyncWithBPMS}, so its attributes are not shared with the BPMS
 * unless one says so. One does: {@link #partnerDeadline}. The deadline on the waiting task
 * is an expression which reads it, so the BPMS needs its value. The cool-off timer carries
 * the literal {@code PT1S} and reads nothing. Apart from that one attribute the BPMS holds
 * the aggregate's ID, because that is how VanillaBP finds the workflow again.
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
   * How long the partner may take to answer, as an ISO-8601 duration such as {@code P3D}.
   * The timer boundary event on the waiting task reads it, so it is shared with the BPMS.
   * It is set once, when the loan is requested, from the configuration of the module. So a
   * test or a demo can shorten it in its own configuration, and the model stays the same.
   *
   * <p>
   * It is a {@code String} and not a {@link java.time.Duration}. A {@code Duration} would
   * reach the model as {@code PT72H}. That fires at the same moment, but an operator reads
   * {@code P3D} more easily.
   * </p>
   *
   * @see <a href=
   *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Timers#a-deadline-out-of-the-workflow-aggregate">A
   *      deadline out of the workflow aggregate</a>
   */
  @SyncWithBPMS
  @Column
  private String partnerDeadline;

  /**
   * The id of the task waiting for the partner's answer, reported by the BPMS when the
   * task was delivered. It is the handle needed to complete that task, and it is null
   * whenever nothing is waiting - which the deadline is what makes true again.
   */
  @Column
  private String partnerApprovalTaskId;

  /** Whether the partner answered, written when their answer arrives. */
  @Column
  private Boolean partnerApproved;

  /** Written by the service task following the completed task. */
  @Column
  private Boolean customerInformed;

  /** Written by the service task the timer boundary event leads to. */
  @Column
  private Boolean timedOut;

}
