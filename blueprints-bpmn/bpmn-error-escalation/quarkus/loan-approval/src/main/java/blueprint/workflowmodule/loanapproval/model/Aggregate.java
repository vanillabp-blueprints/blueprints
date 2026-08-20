package blueprint.workflowmodule.loanapproval.model;

import org.hibernate.annotations.DynamicUpdate;

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
 * {@code @DynamicUpdate} is here because of the non-interrupting escalation, and it is the one
 * line of persistence tuning this blueprint needs. The boundary event catches the escalation
 * without ending the subprocess, so the branch behind it and the task after the throw event run
 * at the same time and both save this entity. Without the annotation each save writes every
 * column, and whichever transaction commits second puts back the values it read at its start:
 * the other branch's write is gone, with no exception and no log line. With it, Hibernate writes
 * only the columns a branch actually changed, and branches that stay off each other's attributes
 * stop colliding. Two branches writing the SAME attribute is a different problem, and one no
 * annotation solves: that needs a {@code @Version} column, or a model that does not do it.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@DynamicUpdate
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

  /** Filled by the business code the rating task of the subprocess triggers. */
  @Column
  private Integer creditRating;

  /** Set when the documents were missing, on the path behind the error boundary event. */
  @Column
  private Boolean documentsRequested;

  /** Set when the contract was signed, which is where the subprocess ends. */
  @Column
  private Boolean contractSigned;

  /** Set on the branch behind the escalation, beside the running subprocess. */
  @Column
  private Boolean supervisorInformed;

}
