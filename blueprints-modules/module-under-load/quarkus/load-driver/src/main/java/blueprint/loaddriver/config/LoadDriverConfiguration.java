package blueprint.loaddriver.config;

import java.util.HashMap;

import blueprint.loaddriver.CreditBureauSimulator;
import blueprint.loaddriver.LoadProfile;
import blueprint.loaddriver.LoadRun;
import blueprint.loaddriver.RunSize;
import blueprint.workflowmodule.loanapproval.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The load driver wires itself. An application which has this JAR on its class path gets the
 * driver, the credit bureau it plays and the two URLs which operate it. Taking the JAR out
 * takes all of it out, and then the application brings a real credit bureau.
 */
@Singleton
public class LoadDriverConfiguration {

  /** The gauge of Agroal which counts the threads waiting for a connection. */
  private static final String CONNECTIONS_WAITING = "agroal.awaiting.count";

  /** The setting which sizes the Agroal pool. */
  private static final String POOL_SETTING = "quarkus.datasource.jdbc.max-size";

  /**
   * The load profile. A wrong key stops the start with a message which names it, instead of
   * failing the first run, because {@link #checkAtStartup} asks for the run while the
   * application starts.
   *
   * @param config Where the profile is.
   * @return The profile.
   */
  @Produces
  @Singleton
  LoadProfile loadProfile(
      final LoadRunConfig config) {

    final var profile = config
        .profile()
        .filter(resource -> !resource.isBlank())
        .orElseThrow(() -> new IllegalStateException("'load-run.profile' is not set. It names"
            + " the load profile on the class path, for this blueprint"
            + " 'loan-approval/load-profile.yaml'."));
    return LoadProfile.read(profile);

  }

  /**
   * The credit bureau, played as the load profile says.
   *
   * @param loanApproval The service the answers go to.
   * @param profile      The load profile.
   * @return The simulator.
   */
  @Produces
  @Singleton
  CreditBureauSimulator creditBureau(
      final Instance<Service> loanApproval,
      final LoadProfile profile) {

    return new CreditBureauSimulator(loanApproval::get, profile);

  }

  /**
   * The load run.
   *
   * @param loanApproval The service of the workflow module.
   * @param profile      The load profile.
   * @param meters       Where the application publishes its meters.
   * @param config       The run sizes.
   * @return The load run.
   */
  @Produces
  @Singleton
  LoadRun loadRun(
      final Instance<Service> loanApproval,
      final LoadProfile profile,
      final MeterRegistry meters,
      final LoadRunConfig config) {

    final var sizes = new HashMap<String, RunSize>();
    config
        .sizes()
        .forEach((
            name,
            size) -> sizes.put(
                name,
                new RunSize(size.stageLength(), size.steps(), size.drainAtMost())));

    return new LoadRun(loanApproval::get, profile, meters, CONNECTIONS_WAITING, POOL_SETTING, sizes, config
        .customerThreads());

  }

  void stopCreditBureau(
      @Disposes final CreditBureauSimulator creditBureau) {

    creditBureau.stop();

  }

  void stopLoadRun(
      @Disposes final LoadRun loadRun) {

    loadRun.stop();

  }

  /**
   * Quarkus creates a bean when it is first used. Asking for the load run here makes the
   * profile and the run sizes checked while the application starts.
   *
   * @param event   The start of the application.
   * @param loadRun The load run.
   */
  void checkAtStartup(
      @Observes final StartupEvent event,
      final LoadRun loadRun) {

    loadRun.report();

  }

}
