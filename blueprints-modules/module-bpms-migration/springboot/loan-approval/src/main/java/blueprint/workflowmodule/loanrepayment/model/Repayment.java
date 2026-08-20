package blueprint.workflowmodule.loanrepayment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate of the second workflow of this module, the one which stays in the
 * old BPMS while loan approvals already start in the new one.
 *
 * <p>
 * Nothing about that is visible here. Which BPMS a workflow starts in is a priority list in
 * the configuration, and this workflow has one of its own.
 * </p>
 */
@Entity(name = "LoanRepayment")
@Table(name = "LOAN_REPAYMENT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repayment {

  /**
   * The natural id of the use case.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String repaymentId;

  /** The amount owed. */
  @Column
  private Integer amount;

  /** Written by the service task of the process. */
  @Column
  private Boolean instalmentBooked;

}
