package blueprint.workflowmodule.riskassessment.model;

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
