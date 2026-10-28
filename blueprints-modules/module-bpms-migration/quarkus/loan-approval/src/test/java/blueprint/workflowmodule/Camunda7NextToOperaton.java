package blueprint.workflowmodule;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

import io.quarkus.arc.profile.IfBuildProfile;
import io.vanillabp.camunda7.engine.Camunda7EngineCustomizer;
import jakarta.inject.Singleton;

/**
 * What a migration from Camunda 7 to Operaton needs besides configuration. Operaton is a fork
 * of Camunda 7, and both engines run inside this application. Only the profile
 * {@code operaton} loads this class.
 *
 * <p>
 * The tables of the two engines are kept apart by configuration: {@code application-operaton.yaml}
 * gives Operaton a datasource of its own.
 * </p>
 *
 * <p>
 * The FEEL engine, which evaluates the expressions of decision tables. Camunda 7 and Operaton
 * bring two versions of the same library. Both end up on the classpath, and the Camunda 7
 * engine does not start with Operaton's version. So the POM removes Camunda's copy, and
 * Camunda 7 uses its older FEEL implementation, which does without that library. It covers
 * what simple decision tables use. This blueprint has no decision at all, but the engine
 * needs the switch to start. The
 * <a href="https://github.com/vanillabp/operaton-adapter#side-by-side">README of the Operaton
 * adapter</a> explains it.
 * </p>
 *
 * <p>
 * This is the one place where this blueprint names classes of an engine. Camunda 7 offers the
 * switch in its engine configuration only.
 * </p>
 */
@Singleton
@IfBuildProfile("operaton")
public class Camunda7NextToOperaton implements Camunda7EngineCustomizer {

  @Override
  public void customize(
      final String adapterId,
      final ProcessEngineConfigurationImpl configuration) {

    configuration.setDmnFeelEnableLegacyBehavior(true);

  }

}
