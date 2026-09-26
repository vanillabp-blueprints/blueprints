package blueprint.workflowmodule.loanapproval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the second section of the process found out: how risky the loan is.
 *
 * <p>
 * This is the object the blueprint is about. The section runs for a big loan only, so a
 * small loan reaches its decision with this object still {@code null} - and it stays
 * {@code null} for the rest of that workflow. A model which reads a value out of it would
 * work for the big loans and stop the small ones.
 * </p>
 *
 * <p>
 * The values below stay in the application, like the ones of the other section. What the
 * BPMS gets to see are the boolean getters of {@link Aggregate}, and those answer without
 * this object.
 * </p>
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessment {

  /** What the customer can put up as a security. Written by the first task of the section. */
  @Column(name = "COLLATERAL_VALUE")
  private Integer collateralValue;

  /** Whether that security covers enough of the loan. Written by the first task as well. */
  @Column(name = "COLLATERAL_SUFFICIENT")
  private Boolean collateralSufficient;

  /** The share of the income already spent on debt. Written by the second task. */
  @Column(name = "DEBT_RATIO")
  private Integer debtRatio;

}
