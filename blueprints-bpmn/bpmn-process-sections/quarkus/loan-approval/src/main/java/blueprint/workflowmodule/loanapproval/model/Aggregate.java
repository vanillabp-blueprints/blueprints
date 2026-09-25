package blueprint.workflowmodule.loanapproval.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * <strong>One sub-object per section.</strong> The process has two sections, and each of
 * them keeps its findings in an object of its own: {@link DocumentCheck} and
 * {@link RiskAssessment}. That is the usual way to carry a big process, and it has one
 * consequence worth knowing: a section which has not run has no object, so the attribute
 * is {@code null}.
 * </p>
 *
 * <p>
 * <strong>The model asks this class, and this class answers with a plain boolean.</strong>
 * The four getters below are the only things the BPMS sees. Each of them checks the
 * sub-object it reads, so a workflow which skipped a section gets an answer instead of an
 * error. A condition which read {@code riskAssessment.collateralSufficient} instead would
 * ask for a value inside an object which is not there, and VanillaBP stops such a workflow
 * rather than making a decision up.
 * </p>
 *
 * <p>
 * The type matters as much as the null check. These getters return {@code boolean} and not
 * {@code Boolean}: a gateway has to take a path, and what a BPMS does with an empty value
 * in a condition differs from engine to engine.
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

  /** Filled by the business code the first service task triggers. */
  @Column
  private Integer creditRating;

  /**
   * Whether this loan is big enough to be looked at by the risk people. Written by the
   * rating step, from the limit in the module's configuration.
   */
  @Column
  private Boolean largeExposure;

  /**
   * The first section, which every loan runs through: what the documents say.
   *
   * <p>
   * It is {@code null} until the section starts, and the tasks inside the section build it.
   * </p>
   */
  @Embedded
  private DocumentCheck documentCheck;

  /**
   * The second section, which a small loan skips: what the risk people found.
   *
   * <p>
   * It stays {@code null} for that whole workflow. Every getter below which reads into it
   * says so in its first line.
   * </p>
   */
  @Embedded
  private RiskAssessment riskAssessment;

  /** The outcome, written by whichever of the two decision tasks ran. */
  @Column
  private String decision;

  /**
   * The question the process asks before the second section: is this loan big enough to be
   * assessed?
   *
   * <p>
   * It reads an attribute of this class and no sub-object, so it is the easy case. It is
   * here because the answer belongs to the loan rather than to the model: the limit may
   * move, and the BPMN keeps asking the same question.
   * </p>
   *
   * @return Whether the risk assessment has to run.
   */
  @SyncWithBPMS
  public boolean isRiskAssessmentRequired() {

    return Boolean.TRUE.equals(largeExposure);

  }

  /**
   * The question the process asks after the second section: did that section run at all?
   *
   * <p>
   * This is the getter to copy. The existence of a section is a fact about the workflow,
   * the model has every right to ask about it, and a plain boolean on this class is the
   * answer. Reaching into {@link #riskAssessment} from the model instead would fail
   * exactly for the workflows which skipped the section.
   * </p>
   *
   * @return Whether the risk assessment ran.
   */
  @SyncWithBPMS
  public boolean isRiskAssessed() {

    return riskAssessment != null;

  }

  /**
   * A finding of the first section, asked by the model of that section while it runs.
   *
   * <p>
   * Inside a section its sub-object is there, because the first task of the section made
   * it. The null check stays anyway: one aggregate serves every model of this workflow
   * module, and a getter which relies on where it is called from is a getter somebody moves
   * one day.
   * </p>
   *
   * @return Whether every document carried the signature it needs.
   */
  @SyncWithBPMS
  public boolean isSignaturesComplete() {

    return (documentCheck != null) && Boolean.TRUE.equals(documentCheck.getSignaturesComplete());

  }

  /**
   * A finding of the second section, asked by the model of that section while it runs.
   *
   * @return Whether the security covers enough of the loan.
   */
  @SyncWithBPMS
  public boolean isCollateralSufficient() {

    return (riskAssessment != null) && Boolean.TRUE.equals(riskAssessment.getCollateralSufficient());

  }

}
