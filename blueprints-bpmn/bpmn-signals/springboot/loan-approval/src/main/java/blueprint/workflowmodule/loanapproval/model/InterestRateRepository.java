package blueprint.workflowmodule.loanapproval.model;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestRateRepository extends JpaRepository<InterestRate, LocalDate> {

  /**
   * The rate published most recently, which is the one the signal announced.
   *
   * @return The latest published rate, if any was published at all.
   */
  Optional<InterestRate> findTopByOrderByPublishedOnDesc();

}
