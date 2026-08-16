package blueprint.workflowmodule.loanapproval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * What one iteration of the multi-instance task found: the offer of one partner.
 *
 * <p>
 * A row per iteration is the shape which survives instances running at the same time. Each
 * of them adds its own row, none of them changes a row of another, and the database does not
 * have to arbitrate between two writers of one value.
 * </p>
 */
@Entity
@Table(name = "PARTNER_OFFER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerOffer {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * The workflow aggregate this offer belongs to. Excluded from
   * {@code toString}/{@code equals} because it points back at the aggregate, which prints
   * its offers.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "LOAN_REQUEST_ID")
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Aggregate loanApproval;

  /** The partner asked, which is the element this iteration was handed. */
  @Column(name = "PARTNER_ID")
  private String partnerId;

  /** Which iteration wrote this row, counted from zero as the BPMS counts. */
  @Column
  private Integer iteration;

  /** What the partner offers, in basis points. */
  @Column
  private Integer rate;

}
