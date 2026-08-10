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
 * Which of these attributes exist after a task has run is the whole point of this
 * blueprint. A handler returning normally saves them, a handler throwing a technical
 * exception saves none of them, and a handler throwing a
 * {@link io.vanillabp.spi.service.TaskException} saves them although it threw.
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

  /**
   * The rating the surrounding system returned. As long as it is null the loan has not
   * been rated yet, which is what makes the task handler idempotent: a task delivered
   * twice does not ask the rating service twice.
   */
  @Column
  private Integer creditRating;

  /**
   * Which rating provider was asked. It comes from the BPMN as an input mapping rather
   * than from the aggregate, so the model can point at another provider without the code
   * changing.
   */
  @Column
  private String ratedBy;

  /**
   * Why the loan was rejected. Written by the handler right before it throws a
   * {@code TaskException}, and still here afterwards: a BPMN error is a business outcome,
   * not a failure, so the transaction commits.
   */
  @Column
  private String rejectionReason;

}
