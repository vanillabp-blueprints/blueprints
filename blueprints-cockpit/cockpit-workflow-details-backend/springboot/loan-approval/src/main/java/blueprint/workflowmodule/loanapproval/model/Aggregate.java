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
 * Besides the data of the loan request, it holds two things about the process. One is the id of
 * the open user task, so the module knows which task is open. The other is the outcome. It is
 * empty while the case runs, and it says how the case ended once it ended: approved, or
 * cancelled on the case page.
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

  /** How a case ended. */
  public enum Outcome {
    /** The loan request was reviewed and approved. The process reached its regular end. */
    APPROVED,
    /** Somebody cancelled the case on its page. The event subprocess ended it. */
    CANCELLED
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

  /** How the case ended. Null while it runs. */
  @Column
  @Enumerated(EnumType.STRING)
  private Outcome outcome;

}
