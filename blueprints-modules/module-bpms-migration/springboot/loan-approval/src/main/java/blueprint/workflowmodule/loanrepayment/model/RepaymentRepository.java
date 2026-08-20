package blueprint.workflowmodule.loanrepayment.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentRepository extends JpaRepository<Repayment, String> {
}
