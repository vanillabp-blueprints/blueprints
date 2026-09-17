package blueprint.workflowmodule.loanapproval.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.envers.repository.config.EnableEnversRepositories;

/**
 * The wiring a workflow module brings along for its own persistence.
 *
 * <p>
 * A repository which reads revisions is built by a factory of its own, and
 * {@code @EnableEnversRepositories} is what puts that factory in place. The base package
 * is the model package of this module, so the module carries its own decision and an
 * application pulling it in has nothing to configure.
 * </p>
 *
 * @see blueprint.workflowmodule.loanapproval.model.AggregateRepository
 */
@Configuration
@EnableEnversRepositories(basePackages = "blueprint.workflowmodule.loanapproval.model")
public class AuditedRepositories {
}
