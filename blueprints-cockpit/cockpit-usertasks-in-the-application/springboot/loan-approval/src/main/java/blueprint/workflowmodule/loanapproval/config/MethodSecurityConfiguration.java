package blueprint.workflowmodule.loanapproval.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Switches on the check of {@code @Secured} on the methods of this module. Each endpoint of a
 * user task form names the {@link Permission} it needs, and Spring Security refuses a call by
 * a user who does not hold it.
 *
 * <p>
 * The check belongs to the module, because only the module knows which user task an endpoint
 * belongs to. It does not depend on the application protecting the URL. A second module which
 * switches the check on as well does no harm, the configuration is the same.
 * </p>
 */
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class MethodSecurityConfiguration {
}
