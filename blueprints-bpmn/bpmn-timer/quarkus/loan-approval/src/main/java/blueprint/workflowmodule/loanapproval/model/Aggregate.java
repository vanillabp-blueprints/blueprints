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
 * unless one says so. Three do. The cool-off timer reads {@link #getCoolOffPeriod()}, a
 * getter which works out its value. The deadline on the waiting task reads
 * {@link #partnerDeadline}, a point in time, and the gateway behind the cool-off timer reads
 * {@link #withdrawn}. So the BPMS needs these three values. Apart from them the BPMS holds the
 * aggregate's ID, because that is how VanillaBP finds the workflow again.
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

  /** From this amount on, a loan is a large one, and the customer gets longer to withdraw. */
  public static final int LARGE_LOAN = 10000;

  /** How long the customer may withdraw a loan below {@link #LARGE_LOAN}. */
  public static final String COOL_OFF_PERIOD = "PT10S";

  /** How long the customer may withdraw a large loan: twice as long. */
  public static final String COOL_OFF_PERIOD_OF_A_LARGE_LOAN = "PT20S";

  /**
   * The natural id of the use case. Using a business identifier instead of a generated
   * one makes a workflow started twice for the same business case a detectable
   * duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/The-workflow-aggregate#natural-ids">Natural ids</a>
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
   * When the partner's time to answer ends, as an ISO-8601 date and time with offset, such
   * as {@code 2026-11-28T17:00:00+01:00}. The timer boundary event on the waiting task reads
   * it, so it is shared with the BPMS. It is set once, when the loan is requested, from the
   * configuration of the module. So a test or a demo can shorten it in its own
   * configuration, and the model stays the same.
   *
   * <p>
   * The offset is what keeps the deadline in the time zone of the business.
   * {@link blueprint.workflowmodule.loanapproval.PartnerDeadline} works it out in that zone.
   * A text without an offset is read in the time zone of the machine, or refused, depending on
   * the BPMS. It is a {@code String} and not an {@code OffsetDateTime}, because
   * the model reads the text as it is written here, the offset included.
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
   * Whether the customer withdrew the request during the cool-off period. The gateway
   * behind the cool-off timer reads it, so it is shared with the BPMS.
   *
   * <p>
   * The customer withdraws while the workflow waits for the timer. At that moment VanillaBP
   * does not talk to the BPMS for this workflow, so the BPMS still holds {@code false}.
   * {@link blueprint.workflowmodule.loanapproval.Workflow#loanWithdrawn} tells it to read
   * the aggregate again.
   * </p>
   */
  @SyncWithBPMS
  @Column
  private boolean withdrawn;

  /** Written by the service task the withdrawn branch leads to. */
  @Column
  private Boolean withdrawalConfirmed;

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

  /**
   * How long the customer may withdraw before the partner is asked, as an ISO-8601 duration.
   * The cool-off timer reads it, so it is shared with the BPMS.
   *
   * <p>
   * It is a getter and not a column. The rule lives here, in Java: a large loan gives the
   * customer twice as long to think it over. The model reads only the name
   * {@code coolOffPeriod}. So the rule can change, for example to count business days, and the
   * BPMN stays the same.
   * </p>
   *
   * <p>
   * The BPMS does not call this method. VanillaBP calls it whenever it talks to the BPMS for
   * this workflow, and the BPMS keeps the result. When the workflow reaches the timer, the BPMS
   * reads the value VanillaBP gave it last. Here that was at the end of the credit rating, the
   * task right before the timer. The amount does not change, so the value is still right. A
   * getter which counts from the current time would be as old as that moment.
   * </p>
   *
   * @return The cool-off period for the amount of this loan.
   * @see <a href=
   *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#what-is-shared-and-when">What
   *      is shared, and when</a>
   */
  @SyncWithBPMS
  public String getCoolOffPeriod() {

    return amount >= LARGE_LOAN ? COOL_OFF_PERIOD_OF_A_LARGE_LOAN : COOL_OFF_PERIOD;

  }

}
