package blueprint.workflowmodule.loanapproval.config;

import static blueprint.workflowmodule.loanapproval.config.LoanApprovalAutoConfiguration.WORKFLOW_MODULE_ID;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.service.registry.ImportHttpServices;

import blueprint.workflowmodule.loanapproval.Cockpit;
import io.vanillabp.cockpit.gui.api.v1.client.autoconfigure.OfficialGuiApiClientAutoConfiguration;
import io.vanillabp.cockpit.support.rest.httpservice.ConditionalOnHttpServiceGroupAddress;
import io.vanillabp.cockpit.support.rest.httpservice.HttpServiceGroupPrefix;

/**
 * The clients of the official GUI API this module asks the cockpit with, see {@link Cockpit}.
 *
 * <p>
 * The interfaces come from {@code official-gui-api-spring-boot-client}. Spring turns them into
 * clients of the HTTP service group {@value #GROUP}, and the support library of the cockpit
 * configures the group from the keys below {@value #KEYS}: {@code base-url}, the timeouts, a
 * proxy, retries and the rest. The keys belong to this module and not to {@code vanillabp}. So a second module in the same
 * application asks the cockpit with values of its own.
 * </p>
 *
 * <p>
 * The group is there only if the application sets {@code loan-approval.cockpit.base-url}. Without
 * it there is no client, the start goes on, and the module does not ask the cockpit. With it the
 * support library checks the address and the other keys at the start.
 * </p>
 *
 * <p>
 * It runs before the auto-configuration of the client library. That one imports the same API
 * into a group of its own, unless the application has clients of the API already, and it has to
 * see the clients of this module to step back. And it runs before the HTTP service clients of
 * Spring Boot, which configure only groups which exist by then.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration(before = {
    OfficialGuiApiClientAutoConfiguration.class, HttpServiceClientAutoConfiguration.class
})
@ConditionalOnHttpServiceGroupAddress(prefix = CockpitClientAutoConfiguration.KEYS)
@ImportHttpServices(group = CockpitClientAutoConfiguration.GROUP, types = {
    Cockpit.Tasklist.class, Cockpit.CurrentUser.class
})
public class CockpitClientAutoConfiguration {

  /** The HTTP service group of the clients. */
  public static final String GROUP = WORKFLOW_MODULE_ID
      + "-cockpit";

  /** Where the keys of the clients are, for example {@code loan-approval.cockpit.base-url}. */
  public static final String KEYS = WORKFLOW_MODULE_ID
      + ".cockpit";

  /**
   * Puts the keys of the group below {@value #KEYS}.
   *
   * @return The prefix of the group.
   */
  @Bean
  public HttpServiceGroupPrefix loanApprovalCockpitKeys() {

    return HttpServiceGroupPrefix.of(GROUP, KEYS);

  }

}
