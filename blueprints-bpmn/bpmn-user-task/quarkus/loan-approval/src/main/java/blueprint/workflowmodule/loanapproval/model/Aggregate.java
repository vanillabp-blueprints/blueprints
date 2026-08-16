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
 * The attribute this blueprint is about is {@link #riskAssessmentTaskId}. A user task
 * waits for somebody outside the BPMS, so something has to remember which task that is
 * until the answer arrives, and the aggregate is where that belongs: it is transactional,
 * it is queryable, and it survives a restart.
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

  /** Filled by the business code the service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * The id of the open risk assessment, reported by the BPMS when the user task was
   * created. It is the handle needed to complete or cancel that task, and it is null
   * whenever no risk assessment is open - before the task exists, and again after it is
   * gone.
   */
  @Column
  private String riskAssessmentTaskId;

  /** What the risk assessment concluded, written when the user task is completed. */
  @Column
  private Boolean riskAcceptable;

  /** Written by the service task following the completed user task. */
  @Column
  private Boolean customerInformed;

  /** Written by the service task the error boundary event of the user task leads to. */
  @Column
  private Boolean withdrawn;

}
