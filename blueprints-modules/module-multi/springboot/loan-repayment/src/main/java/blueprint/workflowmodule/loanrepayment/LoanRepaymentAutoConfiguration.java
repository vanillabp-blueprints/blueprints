package blueprint.workflowmodule.loanrepayment;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import blueprint.workflowmodule.loanrepayment.config.LoanRepaymentProperties;

/**
 * The second workflow module wires itself the same way the first one does, and that is the
 * point of the recipe: it is one class plus one registration file, identical in every
 * module, so an application can collect modules without knowing anything about them.
 *
 * <p>
 * Including the bean name generator, which is what keeps the two modules apart: both have a
 * class called {@code Service} and one called {@code ApiController}, and Spring's default
 * names them after the class alone.
 * </p>
 *
 * @see blueprint.workflowmodule.loanapproval.LoanApprovalAutoConfiguration
 */
@AutoConfiguration
@ComponentScan
@EntityScan
@EnableJpaRepositories(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@EnableConfigurationProperties(LoanRepaymentProperties.class)
public class LoanRepaymentAutoConfiguration {
}
