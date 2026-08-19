package blueprint.workflowmodule.loanapproval.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
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
 * The workflow aggregate: one entity per workflow instance, holding everything the
 * process needs to know. There are no process variables - this is the single source of
 * truth, and it stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * This class is where the blueprint happens, in two steps which belong together.
 * </p>
 *
 * <p>
 * <strong>The model asks questions, this class answers them.</strong> The BPMN references
 * {@link #isApprovableWithoutReview()} and {@link #isWorthAManualReview()}, never
 * {@link #riskClass}. A condition therefore says what the process decides, and the data
 * behind the decision stays free to change: this application replaced a boolean with an
 * enum, and neither the BPMN nor a running workflow noticed.
 * </p>
 *
 * <p>
 * <strong>Only the answers reach the BPMS.</strong> The class is annotated
 * {@code @NoSyncWithBPMS}, so nothing is shared unless it says otherwise, and the two
 * getters the model needs are annotated {@code @SyncWithBPMS}. The BPMS therefore holds
 * three values: the two answers and the workflow aggregate's ID, which VanillaBP
 * always shares because it is how it finds the workflow again. The customer's name never
 * leaves the application, and neither does the rating.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#decoupling-bpmn-from-the-data-model">Decoupling
 *      BPMN from the data model</a>
 */
@Entity
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case: the id of the loan request, not a number a database
   * invented. It identifies the business case, so a workflow started twice for the same
   * request is a detectable duplicate rather than a second case.
   *
   * <p>
   * It is also the one value the annotations cannot keep out: a BPMS without a business key
   * of its own is given the aggregate's ID, because that is how VanillaBP finds the
   * workflow again.
   * </p>
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. Business data - the model never sees it. */
  @Column
  private Integer amount;

  /**
   * The customer, which is personal data. It stays in the application: nothing about this
   * attribute is shared, and the BPMS has no reason to hold a name.
   */
  @Column
  private String customerName;

  /** Filled by the business code the first service task triggers. */
  @Column
  private Integer creditRating;

  /**
   * What the rating counts as. THE data-model attribute of this blueprint: the model does
   * not know it exists, so it may become anything tomorrow.
   */
  @Enumerated(EnumType.STRING)
  @Column
  private RiskClass riskClass;

  /** Which way the process went, written by the task on that branch. */
  @Column
  private String outcome;

  /**
   * The first question the process asks: may this loan be approved without anybody looking
   * at it?
   *
   * <p>
   * Annotated {@code @SyncWithBPMS} because a BPMS evaluates the condition against what
   * VanillaBP shared with it. Nothing else of this class is shared, so the engine holds the
   * two answers and the aggregate's ID, and neither the rating nor the customer.
   * </p>
   *
   * @return Whether the loan can be approved right away.
   */
  @SyncWithBPMS
  public boolean isApprovableWithoutReview() {

    return riskClass == RiskClass.LOW;

  }

  /**
   * The second question: should a person look at this request?
   *
   * @return Whether the request goes to a manual review.
   */
  @SyncWithBPMS
  public boolean isWorthAManualReview() {

    return riskClass == RiskClass.MEDIUM;

  }

}
