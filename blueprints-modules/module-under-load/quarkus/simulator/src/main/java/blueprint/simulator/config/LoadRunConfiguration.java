package blueprint.simulator.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.simulator.loadrun.LoadProfile;
import blueprint.simulator.loadrun.LoadRun;
import blueprint.simulator.loadrun.RunSize;
import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;

/**
 * Wires the load driver from {@link LoadRunProperties}. Every setting is checked while the
 * simulator starts, so a missing one stops the start with a message which names it, instead of
 * failing the first run.
 */
@Configuration
@EnableConfigurationProperties(LoadRunProperties.class)
public class LoadRunConfiguration {

  /**
   * How long one call to the application may take. Under load the application gets slow, and
   * a slow answer is part of the result. Only a call which takes longer than this counts as
   * failed.
   */
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

  /**
   * The load profile. A wrong key stops the start with a message which names it.
   *
   * @param properties Where the profile is.
   * @return The profile.
   */
  @Bean
  public LoadProfile loadProfile(
      final LoadRunProperties properties) {

    if (properties.profile() == null || properties.profile().isBlank()) {
      throw new IllegalStateException("'load-run.profile' is not set. It names the load profile"
          + " on the class path, for this blueprint 'loan-approval/load-profile.yaml'.");
    }
    return LoadProfile.read(properties.profile());

  }

  /**
   * The load run.
   *
   * @param profile     The load profile.
   * @param properties  The run sizes and where the application is.
   * @param restClients The REST client builder of Spring Boot.
   * @return The load run.
   */
  @Bean(destroyMethod = "stop")
  public LoadRun loadRun(
      final LoadProfile profile,
      final LoadRunProperties properties,
      final RestClient.Builder restClients) {

    final var application = properties.application();
    if (application == null) {
      throw new IllegalStateException("'load-run.application' is not set. It says where the"
          + " application under load is: 'base-url', 'metrics-url', 'jmx-address',"
          + " 'connections-waiting-meter' and 'pool-setting'.");
    }
    final var baseUrl = required(application, LoadRunProperties.Application::baseUrl, "base-url",
        "where the API of the application answers, for example http://localhost:8080");
    final var metricsUrl = required(application, LoadRunProperties.Application::metricsUrl, "metrics-url",
        "where the application shows its meters in the Prometheus format");
    final var jmxAddress = required(application, LoadRunProperties.Application::jmxAddress, "jmx-address",
        "where the JVM of the application answers on JMX, for example localhost:9010");
    final var connectionsWaitingMeter = required(application, LoadRunProperties.Application::connectionsWaitingMeter,
        "connections-waiting-meter", "the gauge of its connection pool which counts the threads waiting for a connection");
    final var poolSetting = required(application, LoadRunProperties.Application::poolSetting, "pool-setting",
        "the setting which sizes the connection pool of the application");

    // One client for every call to the application. The client of the JDK keeps its
    // connections open, as the front ends of many customers would.
    final var timeouts = new JdkClientHttpRequestFactory();
    timeouts.setReadTimeout(CALL_TIMEOUT);
    final var calls = restClients
        .clone()
        .requestFactory(timeouts)
        .build();
    final var loanApproval = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(calls
            .mutate()
            .baseUrl(baseUrl)
            .build()))
        .build()
        .createClient(LoanApprovalApi.class);

    final var sizes = properties.sizes() == null
        ? Map.<String, RunSize>of()
        : new LinkedHashMap<>(properties.sizes());
    return new LoadRun(
        loanApproval, profile, calls, metricsUrl, connectionsWaitingMeter, jmxAddress, poolSetting, sizes, properties
            .customerThreads());

  }

  private static String required(
      final LoadRunProperties.Application application,
      final Function<LoadRunProperties.Application, String> value,
      final String key,
      final String meaning) {

    final var found = value.apply(application);
    if (found == null || found.isBlank()) {
      throw new IllegalStateException("'load-run.application."
          + key
          + "' is not set. It is "
          + meaning
          + ".");
    }
    return found;

  }

}
