package blueprint.workflowmodule.loanapproval.model;

import org.hibernate.envers.Audited;

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
 * The one word this blueprint adds is {@code @Audited}. Hibernate Envers then writes a
 * row into {@code LOAN_APPROVAL_AUD} for every change, together with the number of the
 * revision it belongs to, and that row is never changed again. So the table below holds
 * the loan approval as it is, and the audit table holds it as it was at every moment
 * before.
 * </p>
 *
 * <p>
 * Auditing an aggregate is the application's decision and costs what it costs: a second
 * write per change and a table which only grows. It is worth it where somebody has to be
 * able to say later who changed what, and it is not worth it anywhere else.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@Audited
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

  /** The amount requested. Written by the API before the workflow starts. */
  @Column
  private Integer amount;

  /** Written by the service task in front of the risk assessment. */
  @Column
  private Integer creditRating;

  /**
   * The id of the open user task, kept so the application can complete it when the
   * answer arrives.
   */
  @Column
  private String assessRiskTaskId;

  /** What the risk assessment decided, written by the person who took the decision. */
  @Column
  private Boolean riskAcceptable;

  /** Who took that decision. It is also the author of the revision it produced. */
  @Column
  private String decidedBy;

  /**
   * Written by the task after the decision. This is the attribute which tells the state
   * of the decision apart from the state of today.
   */
  @Column
  private Boolean paidOut;

}
