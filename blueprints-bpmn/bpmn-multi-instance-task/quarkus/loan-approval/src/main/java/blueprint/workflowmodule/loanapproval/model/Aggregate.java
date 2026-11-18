package blueprint.workflowmodule.loanapproval.model;

import java.util.ArrayList;
import java.util.List;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
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
 * <p>
 * Of those two only the first one reaches the BPMS. The class is annotated
 * {@code @NoSyncWithBPMS}, so nothing is shared unless it says otherwise, and
 * {@link #partnerIds} is annotated {@code @SyncWithBPMS} because the model iterates over
 * it. The engine has to see that list to make one instance per element. The offers do not
 * go there, because no expression in the model reads them.
 * </p>
 *
 * <p>
 * The second multi-instance task sends the contract, once per recipient. It is based on a
 * number, not on a collection. So only {@link #getContractCopies()} reaches the BPMS, and
 * the recipients stay here, in {@link #contractRecipients()}.
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
@NoSyncWithBPMS
public class Aggregate {

  /** Who gets the first copy of the contract. The partner chosen gets the second one. */
  public static final String CUSTOMER = "customer";

  /**
   * The natural id of the use case. Using a business identifier instead of a generated
   * one makes a workflow started twice for the same business case a detectable
   * duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/The-workflow-aggregate#natural-ids">Natural ids</a>
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
   * Annotated {@code @SyncWithBPMS} because the model iterates over it: Camunda 7 reads it
   * as {@code camunda:collection}, Camunda 8 as {@code inputCollection}, and both engines
   * need the elements themselves, not their number - each instance is handed one of them.
   * The list travels as it is, which is the second reason to keep it to identifiers.
   * </p>
   *
   * <p>
   * Loaded eagerly on purpose. VanillaBP reads this collection whenever it hands the
   * aggregate to the BPMS, which can be outside any place a lazy collection could still be
   * initialized.
   * </p>
   */
  @SyncWithBPMS
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
   * How many copies of the contract went out so far, written by the second multi-instance
   * task. Its instances run one after the other, so one counter is safe here.
   */
  @Column
  private Integer contractCopiesSent;

  /**
   * Who gets a copy of the contract: the customer, and the partner chosen once there is one.
   * Nothing of this reaches the BPMS. The business code reads the recipient of an iteration
   * from here, by its index.
   *
   * @return The recipients, in the order the copies are sent.
   */
  public List<String> contractRecipients() {

    if (chosenPartnerId == null) {
      return List.of(CUSTOMER);
    }
    return List.of(CUSTOMER, chosenPartnerId);

  }

  /**
   * How many copies of the contract the model sends. The second multi-instance task reads it
   * as its number of iterations, so it is annotated {@code @SyncWithBPMS}. Only the number
   * reaches the BPMS, not the recipients.
   *
   * @return The number of copies.
   */
  @SyncWithBPMS
  public int getContractCopies() {

    return contractRecipients().size();

  }

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
