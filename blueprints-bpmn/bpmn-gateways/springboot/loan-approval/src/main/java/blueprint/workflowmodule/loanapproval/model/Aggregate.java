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

  /** Which way the gateway sent the workflow, written by the task on that branch. */
  @Column
  private String outcome;

}
