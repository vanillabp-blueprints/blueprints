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
 * What one iteration of the multi-instance SUBPROCESS produced: the best offer of one
 * region, written by a task which is not multi-instance itself but runs inside that
 * iteration.
 */
@Entity
@Table(name = "REGION_RESULT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionResult {

  @Id
  @GeneratedValue
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "LOAN_REQUEST_ID")
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Aggregate loanApproval;

  /** The region this row summarises. */
  @Column(name = "REGION_ID")
  private String regionId;

  /** Which iteration of the subprocess wrote it, counted from zero as the BPMS counts. */
  @Column
  private Integer iteration;

  /** The partner with the best offer in that region. */
  @Column(name = "BEST_PARTNER_ID")
  private String bestPartnerId;

  /** That offer's rate, in basis points. */
  @Column(name = "BEST_RATE")
  private Integer bestRate;

}
