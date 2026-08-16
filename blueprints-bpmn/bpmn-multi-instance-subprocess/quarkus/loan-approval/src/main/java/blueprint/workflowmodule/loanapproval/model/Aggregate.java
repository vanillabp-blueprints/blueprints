package blueprint.workflowmodule.loanapproval.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
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
 * Two collections are iterated over, and they belong to different levels of the model:
 * {@link #regionIds} is what the subprocess repeats for, {@link #partnerIds} what the task
 * inside it repeats for. Both are written before the subprocess is reached.
 * </p>
 *
 * <p>
 * Everything the iterations produce is a ROW, never an attribute they share. Iterations run
 * next to each other, each of them saves this aggregate, and the one committing last would
 * put back what it read at its start.
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

  /** Filled by the business code the first service task of the process triggers. */
  @Column
  private Integer creditRating;

  /** What the multi-instance SUBPROCESS iterates over: one assessment per region. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_REGION", joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"))
  @Column(name = "REGION_ID")
  @Builder.Default
  private List<String> regionIds = new ArrayList<>();

  /** What the multi-instance TASK inside the subprocess iterates over. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_PARTNER", joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"))
  @Column(name = "PARTNER_ID")
  @Builder.Default
  private List<String> partnerIds = new ArrayList<>();

  /** One row per inner iteration: what a partner offers in one region. */
  @OneToMany(mappedBy = "loanApproval", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @Builder.Default
  private List<PartnerOffer> offers = new ArrayList<>();

  /** One row per outer iteration: the best offer of one region. */
  @OneToMany(mappedBy = "loanApproval", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @Builder.Default
  private List<RegionResult> regionResults = new ArrayList<>();

  /** Written after the last region, by the task following the subprocess. */
  @Column
  private String chosenRegionId;

  /** The partner of the offer chosen. */
  @Column
  private String chosenPartnerId;

  /** The rate of the offer chosen, in basis points. */
  @Column
  private Integer chosenRate;

  /**
   * Adds the offer of one partner in one region.
   *
   * @param offer The offer of one inner iteration.
   */
  public void addOffer(
      final PartnerOffer offer) {

    offer.setLoanApproval(this);
    offers.add(offer);

  }

  /**
   * Adds the result of one region.
   *
   * @param result The result of one outer iteration.
   */
  public void addRegionResult(
      final RegionResult result) {

    result.setLoanApproval(this);
    regionResults.add(result);

  }

}
