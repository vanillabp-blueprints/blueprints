package blueprint.workflowmodule.loanapproval.model;

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
   * What the rating means, decided by the business code and read by the gateway:
   * <code>acceptable</code>, <code>review</code> or <code>too-low</code>. The BPMN asks
   * this one attribute rather than comparing numbers itself, so a threshold can move
   * without the model being touched - and because it holds exactly one of three values,
   * no two conditions of the gateway can be true at once.
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
   * @return Whether the rating is good enough.
   */
  public boolean isRatedAcceptable() {

    return "acceptable".equals(ratingBand);

  }

  /**
   * The second half of the same decision: should a person look at this request?
   *
   * @return Whether the request goes to a manual review.
   */
  public boolean isRatedForManualReview() {

    return "review".equals(ratingBand);

  }

}
