package blueprint.loaddriver.config;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import blueprint.loaddriver.CreditBureauSimulator;
import blueprint.loaddriver.LoadProfile;
import blueprint.loaddriver.LoadRun;
import blueprint.loaddriver.LoadRunController;
import blueprint.loaddriver.RunSize;
import blueprint.workflowmodule.loanapproval.Service;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The load driver wires itself. An application which has this JAR on its class path gets the
 * driver, the credit bureau it plays and the two URLs which operate it. Taking the JAR out
 * takes all of it out, and then the application brings a real credit bureau.
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LoadRunProperties.class)
@Import(LoadRunController.class)
public class LoadDriverAutoConfiguration {

  /** The gauge of HikariCP which counts the threads waiting for a connection. */
  private static final String CONNECTIONS_WAITING = "hikaricp.connections.pending";

  /** The setting which sizes the HikariCP pool. */
  private static final String POOL_SETTING = "spring.datasource.hikari.maximum-pool-size";

  /**
   * The load profile, read while the application starts. A wrong key stops the start with a
   * message which names it, instead of failing the first run.
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
   * The credit bureau, played as the load profile says.
   *
   * @param loanApproval The service the answers go to.
   * @param profile      The load profile.
   * @return The simulator.
   */
  @Bean(destroyMethod = "stop")
  public CreditBureauSimulator creditBureau(
      final ObjectProvider<Service> loanApproval,
      final LoadProfile profile) {

    return new CreditBureauSimulator(loanApproval::getObject, profile);

  }

  /**
   * The load run.
   *
   * @param loanApproval The service of the workflow module.
   * @param profile      The load profile.
   * @param meters       Where the application publishes its meters.
   * @param properties   The run sizes.
   * @return The load run.
   */
  @Bean(destroyMethod = "stop")
  public LoadRun loadRun(
      final ObjectProvider<Service> loanApproval,
      final LoadProfile profile,
      final MeterRegistry meters,
      final LoadRunProperties properties) {

    final var sizes = properties.sizes() == null ? Map.<String, RunSize>of() : properties.sizes();
    return new LoadRun(
        loanApproval::getObject, profile, meters, CONNECTIONS_WAITING, POOL_SETTING, sizes, properties
            .customerThreads());

  }

}
