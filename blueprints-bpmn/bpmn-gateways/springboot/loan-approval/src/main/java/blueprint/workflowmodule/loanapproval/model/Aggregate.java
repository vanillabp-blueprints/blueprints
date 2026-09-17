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
 * This is what the gateway of this blueprint reads. A condition in the BPMN names an
 * attribute of this class, so what the process may decide on is visible here, in Java,
 * with a type and a comment.
 * </p>
 *
 * <p>
 * The conditions also decide what the BPMS gets to see. This class is annotated
 * {@code @NoSyncWithBPMS}, so nothing is shared unless it says otherwise, and what a
 * condition reads carries {@code @SyncWithBPMS}: the two getters the first gateway asks,
 * and {@link #amount}, which the second gateway compares itself. Everything else stays in
 * the application, the credit rating and the band behind the answers included.
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

  /**
   * The amount requested. It is shared with the BPMS because the second gateway compares
   * it in the model, and that is what the shortcut costs: a raw attribute a condition
   * reads has to travel, while the answers of the first gateway could have hidden it.
   */
  @Column
  @SyncWithBPMS
  private Integer amount;

  /**
   * Filled by the business code the first service task of the process triggers. No
   * condition reads it, so it never leaves the application.
   */
  @Column
  private Integer creditRating;

  /**
   * What the rating means, decided by the business code and read by the gateway:
   * <code>acceptable</code>, <code>review</code> or <code>too-low</code>. The BPMN asks
   * this one attribute rather than comparing numbers itself, so a threshold can move
   * without the model being touched - and because it holds exactly one of three values,
   * no two conditions of the gateway can be true at once. The model asks the getters
   * below, so this column stays out of the BPMS as well.
   */
  @Column
  private String ratingBand;

  /** Which way the gateways sent the workflow, written by the task on that branch. */
  @Column
  private String outcome;

  /** How the customer was told about the decision, written on the approved branch. */
  @Column
  private String notifiedBy;

  /**
   * The first decision of the process, expressed as the question the BPMN asks: may this
   * loan be approved without anybody looking at it?
   *
   * <p>
   * The gateway references this getter rather than {@link #ratingBand}, which is the
   * technique the wiki recommends: the model asks a question, the aggregate answers it,
   * and the data behind the answer stays free to change. Turning
   * <code>ratingBand</code> into an enum, a number or three separate columns later is a
   * migration of this class alone - the BPMN and every workflow already running keep
   * working.
   * </p>
   *
   * <p>
   * Annotated {@code @SyncWithBPMS} because a BPMS evaluates the condition against what
   * VanillaBP shared with it. The answer travels, the value behind it does not.
   * </p>
   *
   * @return Whether the rating is good enough.
   */
  @SyncWithBPMS
  public boolean isRatedAcceptable() {

    return "acceptable".equals(ratingBand);

  }

  /**
   * The second half of the same decision: should a person look at this request?
   *
   * @return Whether the request goes to a manual review.
   */
  @SyncWithBPMS
  public boolean isRatedForManualReview() {

    return "review".equals(ratingBand);

  }

}
