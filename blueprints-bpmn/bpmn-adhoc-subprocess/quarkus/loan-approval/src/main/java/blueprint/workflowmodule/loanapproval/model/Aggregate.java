package blueprint.workflowmodule.loanapproval.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
 * The attribute this blueprint is about is {@link #checksToRun}. It holds the ids of the
 * BPMN elements inside the ad-hoc subprocess which are supposed to run, VanillaBP shares
 * the aggregate with the BPMS on every command it sends, and a collection travels as a
 * list, so the expression of the model can read it. Nothing else is needed to drive the
 * element.
 * </p>
 *
 * <p>
 * {@link #version} is here because the checks run at the same time and each of them writes
 * this row. Without it the transaction committing second would write back what it read at
 * its start and the other check's result would be gone, with no exception and no log line.
 * With it the collision is an optimistic locking exception, VanillaBP passes it on and the
 * BPMS retries the task. Which other ways there are to deal with two writers, and when each
 * of them fits, is shown by
 * <a href="https://github.com/vanillabp-blueprints/persistence-parallel-branches-quarkus">
 * <code>persistence-parallel-branches</code></a>.
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

  /**
   * Guards this row against the checks which run at the same time. It is also what keeps
   * VanillaBP quiet: an aggregate without a version attribute earns a warning at startup
   * naming the elements of the model which can put a second token into the workflow, and
   * the ad-hoc subprocess is one of them.
   */
  @Version
  private Long version;

  /** The amount requested. It decides who picks the additional checks. */
  @Column
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * The id of the open selection of checks, reported by the BPMS when the user task was
   * created. It is null whenever no selection is open, and it stays null for a request the
   * decision table decides.
   */
  @Column
  private String selectChecksTaskId;

  /**
   * The ids of the BPMN elements the ad-hoc subprocess is supposed to activate, read by the
   * FEEL expression of the model.
   *
   * <p>
   * It is filled only where a case worker picked. The decision table writes its result
   * straight into a process variable of the same name, so on that path this attribute stays
   * empty and what ran is visible in the checks below - which is what it looks like when no
   * Java is involved at all.
   * </p>
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_CHECKS", joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"))
  @OrderColumn(name = "POSITION")
  @Column(name = "ELEMENT_ID")
  @Builder.Default
  private List<String> checksToRun = new ArrayList<>();

  /** Written by the check of the same name, if it was activated. */
  @Column
  private Boolean incomeChecked;

  /** Written by the check of the same name, if it was activated. */
  @Column
  private Boolean fraudChecked;

  /** Written by the check of the same name, if it was activated. */
  @Column
  private Boolean collateralChecked;

  /** Written by the service task behind the ad-hoc subprocess, so it says the element was left. */
  @Column
  private Boolean customerInformed;

}
