package blueprint.workflowmodule.riskassessment.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate of the risk assessment.
 *
 * <p>
 * The entity is given a name of its own. Two JPA entities called {@code Aggregate} in one
 * persistence unit would clash, and every use case of the reference structure has a class of
 * that name - so the second module in an application says which entity it is.
 * </p>
 *
 * <p>
 * It holds the id of the case it was asked about, and that id is the caller's, not this
 * module's. That is the only thing crossing the border, and it is a string rather than a
 * class of the other module.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so none of its attributes is shared with
 * the BPMS. The model of this process reads nothing: it has no condition, no timer and no
 * multi-instance task, and it waits for no message. The amount and the score stay in the
 * application, and the BPMS holds the workflow aggregate's ID alone, which VanillaBP always
 * shares because it is how it finds the workflow again.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity(name = "RiskAssessment")
@Table(name = "RISK_ASSESSMENT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case: the case the assessment was asked for.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String caseId;

  /** The amount at risk, as the caller stated it. */
  @Column
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  @Column
  private Integer score;

}
