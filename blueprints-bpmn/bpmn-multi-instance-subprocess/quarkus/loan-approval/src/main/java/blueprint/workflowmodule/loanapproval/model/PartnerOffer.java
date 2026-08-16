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
 * What one iteration of the multi-instance TASK found: the offer of one partner, in one
 * region. Which region is part of the row, because the task runs inside the iteration of
 * the subprocess and would otherwise not be able to say where its offer belongs.
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "LOAN_REQUEST_ID")
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Aggregate loanApproval;

  /** The region this offer was asked for - the element of the OUTER iteration. */
  @Column(name = "REGION_ID")
  private String regionId;

  /** The partner asked - the element of the INNER iteration. */
  @Column(name = "PARTNER_ID")
  private String partnerId;

  /** What the partner offers, in basis points. */
  @Column
  private Integer rate;

}
