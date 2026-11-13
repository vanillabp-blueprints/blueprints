package blueprint.workflowmodule.loanapproval.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Switches on the checks of {@code @Secured} and {@code @PreAuthorize} on the methods of this
 * module. Each endpoint of a user task form names the {@link Permission} it needs, and Spring
 * Security refuses a call by a user who does not hold it.
 *
 * <p>
 * {@code @PreAuthorize} is needed for the endpoints which read a form. They let in a second
 * kind of user as well: somebody who worked on the task before, whatever their authorities are
 * today. That is a question about the case, so a bean of the module answers it (see
 * {@code UserTaskAccess}).
 * </p>
 *
 * <p>
 * The checks belong to the module, because only the module knows which user task an endpoint
 * belongs to. They do not depend on the application protecting the URL. A second module which
 * switches the checks on as well does no harm, the configuration is the same.
 * </p>
 */
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class MethodSecurityConfiguration {
}
