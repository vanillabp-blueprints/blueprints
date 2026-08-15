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
 * Two attributes belong to the multi-instance task. {@link #partnerIds} is what it
 * iterates over, and the model reads it from here rather than from a variable somebody
 * pushed. {@link #offers} is where the iterations put their results: one row per instance,
 * added and never changed, because the instances run at the same time and each of them
 * saves this aggregate.
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

  /**
   * The partners to ask, written before the multi-instance task is reached and not
   * touched afterwards. The BPMN iterates over this attribute, so it holds identifiers
   * rather than objects: each iteration is handed one element, and the business code looks
   * up whatever else it needs.
   *
   * <p>
   * Loaded eagerly on purpose. On an embedded engine the model reads this collection while
   * evaluating the multi-instance task, outside any place a lazy collection could still be
   * initialized.
   * </p>
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_PARTNER", joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"))
  @Column(name = "PARTNER_ID")
  @Builder.Default
  private List<String> partnerIds = new ArrayList<>();

  /**
   * What the iterations found, one row each. Rows are only ever added, which is what makes
   * this safe while several instances of the task run next to each other: two of them
   * writing the same attribute would mean the one committing second puts back what it read.
   */
  @OneToMany(mappedBy = "loanApproval", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @Builder.Default
  private List<PartnerOffer> offers = new ArrayList<>();

  /** Written after the last iteration by the task following the multi-instance one. */
  @Column
  private String chosenPartnerId;

  /** The rate of the offer chosen, in basis points. */
  @Column
  private Integer chosenRate;

  /**
   * Adds an offer of one iteration.
   *
   * @param offer The offer of one partner.
   */
  public void addOffer(
      final PartnerOffer offer) {

    offer.setLoanApproval(this);
    offers.add(offer);

  }

}
