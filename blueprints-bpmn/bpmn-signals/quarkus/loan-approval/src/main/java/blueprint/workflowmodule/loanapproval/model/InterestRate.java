package blueprint.workflowmodule.loanapproval.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The interest rate published for a day. Not a workflow aggregate: it belongs to no
 * workflow, and several loan approvals read the same row.
 *
 * <p>
 * It exists because a signal transports nothing. A message can at least be preceded by a
 * write to the aggregate it is correlated for; a broadcast has no aggregate, so data a
 * waiting workflow needs has to be somewhere it can read on its own. That "somewhere" is
 * ordinary application data - the BPMS neither knows nor needs this table.
 * </p>
 */
@Entity
@Table(name = "INTEREST_RATE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestRate {

  /**
   * The day the rate was published for. Not called 'day': that is a reserved word in SQL,
   * and a blueprint is copied.
   */
  @Id
  private LocalDate publishedOn;

  /** The rate itself, in percent. */
  @Column
  private BigDecimal percentage;

}
