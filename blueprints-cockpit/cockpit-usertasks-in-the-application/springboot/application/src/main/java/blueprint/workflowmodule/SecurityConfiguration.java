package blueprint.workflowmodule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
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
 * This blueprint logs in with HTTP Basic against two demo users, so a browser is enough to try
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
   * Two demo users, one per kind of user task. The password is {@code demo} for both. A real
   * application does not keep users here.
   *
   * @return The demo users.
   */
  @Bean
  public UserDetailsService demoUsers() {

    return new InMemoryUserDetailsManager(
        User
            .withUsername("risk-officer")
            .password("{noop}demo")
            .authorities(Permission.ASSESS_RISK)
            .build(), User
                .withUsername("clerk")
                .password("{noop}demo")
                .authorities(Permission.CHECK_DOCUMENT)
                .build());

  }

}
