package blueprint.workflowmodule.loanapproval.api.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.service.registry.ImportHttpServices;

import io.vanillabp.cockpit.support.rest.httpservice.ConditionalOnHttpServiceGroupAddress;
import io.vanillabp.cockpit.support.rest.httpservice.HttpServiceGroupPrefix;

/**
 * Builds the client of the API of the user interface of 'loan-approval', so an application adds
 * this module, sets the keys and injects {@link LoanApprovalApi}. Spring turns the interface into a
 * client of the HTTP service group {@value #GROUP}. The support library of the Business Cockpit
 * configures the group from the keys below {@value #KEYS}: {@code base-url}, the timeouts, a proxy,
 * retries, OAuth and the rest.
 *
 * <p>
 * The group is there only if the application sets {@code loan-approval-gui-api.client.base-url}.
 * The process driver uses the client, and an application has the driver only in a build for
 * tests. Without the key, the start goes on and there is no client.
 * </p>
 *
 * <p>
 * It runs before the HTTP service clients of Spring Boot, which configure only groups which
 * exist by then. It steps back if the application imports the API into a group of its own.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration(before = HttpServiceClientAutoConfiguration.class)
@ConditionalOnHttpServiceGroupAddress(prefix = LoanApprovalClientAutoConfiguration.KEYS)
@ConditionalOnMissingBean(LoanApprovalApi.class)
@ImportHttpServices(group = LoanApprovalClientAutoConfiguration.GROUP, types = LoanApprovalClient.class)
public class LoanApprovalClientAutoConfiguration {

  /** The HTTP service group of the client. */
  public static final String GROUP = "loan-approval-gui-api";

  /** Where the keys of the client are, for example {@code loan-approval-gui-api.client.base-url}. */
  public static final String KEYS = "loan-approval-gui-api.client";

  /**
   * Puts the keys of the group below {@value #KEYS}.
   *
   * @return The prefix of the group.
   */
  @Bean
  public HttpServiceGroupPrefix loanApprovalGuiApiClientKeys() {

    return HttpServiceGroupPrefix.of(GROUP, KEYS);

  }

}
