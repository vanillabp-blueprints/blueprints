package blueprint.workflowmodule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import blueprint.workflowmodule.loanapproval.config.Permission;

/**
 * How users log in. This is the part of security which belongs to the application: it is the
 * same for every workflow module the application hosts, and it differs from one deployment to
 * the next. Which user may work on which user task is decided by the modules themselves (see
 * {@link Permission}).
 *
 * <p>
 * This blueprint logs in with HTTP Basic against six demo users, so a browser is enough to try
 * it. A real application takes its users from an identity provider instead, and maps their
 * roles to the authorities of its modules.
 * </p>
 */
@Configuration
public class SecurityConfiguration {

  /**
   * Every request below {@code /api} needs a user who logged in. Everything else is not
   * touched, for example the web applications of an embedded BPMS, which bring their own
   * login.
   *
   * <p>
   * CSRF protection is off, because the API takes no cookies: every request carries its
   * credentials, and no session is kept. An application whose forms use a session cookie
   * keeps CSRF protection on.
   * </p>
   *
   * @param http The builder of the filter chain.
   * @return The filter chain for the API.
   * @throws Exception If the chain cannot be built.
   */
  @Bean
  public SecurityFilterChain api(
      final HttpSecurity http) throws Exception {

    return http
        .securityMatcher("/api/**")
        .authorizeHttpRequests(requests -> requests
            .anyRequest()
            .authenticated())
        .httpBasic(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .build();

  }

  /**
   * The demo users. The password is {@code demo} for all of them. A real application does not
   * keep users here.
   *
   * <ul>
   * <li>{@code alice} and {@code erin} are advisors. They request loans and call their
   * customers.</li>
   * <li>{@code lea} leads the advisors and hands a customer to another advisor.</li>
   * <li>{@code bob} assesses risks.</li>
   * <li>{@code carol} assesses risks and approves loans, so the four-eyes principle matters
   * for her.</li>
   * <li>{@code dave} approves loans.</li>
   * </ul>
   *
   * @return The demo users.
   */
  @Bean
  public UserDetailsService demoUsers() {

    return new InMemoryUserDetailsManager(
        user("alice", Permission.REQUEST_LOAN, Permission.CALL_CUSTOMER), user("erin", Permission.REQUEST_LOAN,
            Permission.CALL_CUSTOMER), user("lea", Permission.CHANGE_ADVISOR), user("bob",
                Permission.ASSESS_RISK), user("carol", Permission.ASSESS_RISK,
                    Permission.APPROVE_LOAN), user("dave", Permission.APPROVE_LOAN));

  }

  private static UserDetails user(
      final String name,
      final String... authorities) {

    return User
        .withUsername(name)
        .password("{noop}demo")
        .authorities(authorities)
        .build();

  }

}
