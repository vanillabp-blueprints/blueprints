package blueprint.workflowmodule.riskassessment;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import blueprint.workflowmodule.riskassessment.config.RiskAssessmentProperties;

/**
 * The second workflow module wires itself the same way the first one does: one class plus one
 * registration file, identical in every module, so an application can collect modules without
 * knowing anything about them.
 *
 * <p>
 * Here it does one thing more, and it is the thing this blueprint is about: the bean
 * implementing {@link blueprint.api.RiskAssessments} is contributed from within this module.
 * The module offering an API brings the implementation along; the module using it sees an
 * interface and a JAR without either of them naming the other.
 * </p>
 *
 * <p>
 * Including the bean name generator, which is what keeps the two modules apart: both have a
 * class called {@code Service} and one called {@code ApiController}, and Spring's default
 * names them after the class alone. Here they are called
 * {@code risk-assessment_<SimpleName>}.
 * </p>
 *
 * @see blueprint.workflowmodule.loanapproval.LoanApprovalAutoConfiguration
 */
@AutoConfiguration
@ComponentScan(nameGenerator = RiskAssessmentBeanNames.class)
@EntityScan
@EnableJpaRepositories(nameGenerator = RiskAssessmentBeanNames.class)
@EnableConfigurationProperties(RiskAssessmentProperties.class)
public class RiskAssessmentAutoConfiguration {
}
