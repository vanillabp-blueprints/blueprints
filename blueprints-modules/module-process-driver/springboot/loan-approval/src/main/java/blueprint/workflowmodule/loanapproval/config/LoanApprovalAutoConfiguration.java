package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import blueprint.workflowmodule.loanapproval.Service;

/**
 * The workflow module wires itself. An application which depends on it does nothing else.
 *
 * <p>
 * The application of this blueprint sits in another package, so its component scan does not
 * reach the module. The module says what it brings: its beans, its aggregate and the repository,
 * and its properties. Every scan names the package of the module by {@link Service}. Without
 * that, a scan would only search {@code config}.
 * </p>
 *
 * <p>
 * The module does not wire the adapters of its ports. Each adapter brings an
 * auto-configuration of its own, and the application decides which adapters it takes.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@ComponentScan(
    basePackageClasses = Service.class,
    // The scan reaches this class as well. Registered twice, it would be applied twice.
    excludeFilters = @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class))
@EntityScan(basePackageClasses = Service.class)
@EnableJpaRepositories(basePackageClasses = Service.class)
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class LoanApprovalAutoConfiguration {
}
