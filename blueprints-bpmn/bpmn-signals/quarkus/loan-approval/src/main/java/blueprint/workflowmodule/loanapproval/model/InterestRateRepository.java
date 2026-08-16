package blueprint.workflowmodule.loanapproval.model;

import java.time.LocalDate;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The published interest rates. Ordinary application data, not a workflow aggregate: it
 * belongs to no business case, which is exactly why the signal can do without a payload.
 *
 * <p>
 * The query is written out here. A Panache repository derives nothing from a method name,
 * so what the method is called and what it queries are two separate decisions.
 * </p>
 */
@ApplicationScoped
public class InterestRateRepository implements PanacheRepositoryBase<InterestRate, LocalDate> {

  /**
   * The rate published most recently, which is the one the signal announced.
   *
   * @return The latest published rate, if any was published at all.
   */
  public Optional<InterestRate> findLatestPublished() {

    return find("order by publishedOn desc").firstResultOptional();

  }

}
