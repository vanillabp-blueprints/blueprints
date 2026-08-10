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

  /** Filled by {@link #assessCreditRating(int)}. */
  @Column
  private Integer creditRating;

  /**
   * Business logic about the business object itself belongs to the business object. The
   * workflow only says when it happens, never what it is.
   *
   * <p>
   * A real application would rate a loan by asking a rating service. What must not happen
   * is putting the calculation into the {@code @WorkflowTask} method that triggers it: the
   * rating is a property of this loan request, not of the process which happens to compute
   * it, and it stays correct if the process is remodelled.
   * </p>
   *
   * @param ratingScale The highest rating that may be awarded.
   */
  public void assessCreditRating(
      final int ratingScale) {

    creditRating = Math.min(ratingScale, amount / 100);

  }

}
