package blueprint.workflowmodule.loanapproval.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the process
 * needs to know. There are no process variables. This is the single source of truth, and it
 * stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * Besides the data of the loan request, it holds the id of the open user task, so the module
 * knows which task is open, and the status of the loan request. The status is business data: a
 * withdrawn loan request is a negative business outcome, but its process still ends the normal
 * way. Only when somebody of operations cancels the process in the BPMS does the case end without
 * reaching an end event. The status does not change then, see the README.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so none of these attributes goes to the
 * BPMS. The Business Cockpit does not read the aggregate either. It gets what the details
 * provider in {@code WorkflowTaskHandler} hands over.
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

  /** Where the loan request stands, in business terms. */
  public enum Status {
    /** The loan request waits for its review. */
    REQUESTED,
    /** The loan request was reviewed and approved. */
    APPROVED,
    /**
     * The loan request is not needed any more, and somebody withdrew it on the case page. The
     * event subprocess "Withdraw loan request" set this status, and the process ended the normal
     * way.
     */
    WITHDRAWN
  }

  /**
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate. The
   * cockpit shows it as the business id of the case.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** Who asks for the loan. */
  @Column
  private String applicant;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** The id of the open user task "Review loan request", null whenever that task is not open. */
  @Column
  private String reviewTaskId;

  /** Where the loan request stands. */
  @Column
  @Enumerated(EnumType.STRING)
  private Status status;

}
