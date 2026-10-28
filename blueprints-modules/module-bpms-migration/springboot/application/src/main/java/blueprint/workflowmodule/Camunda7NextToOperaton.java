package blueprint.workflowmodule;

import javax.sql.DataSource;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vanillabp.camunda7.engine.Camunda7EngineCustomizer;

/**
 * What a migration from Camunda 7 to Operaton needs besides configuration. Operaton is a fork
 * of Camunda 7, and both engines run inside this application. So they have to be kept apart in
 * two places. Only the profile {@code operaton} loads this class.
 *
 * <p>
 * The tables. Two engines must not share one set of tables. Camunda 7 keeps the tables it had
 * before the migration, in the application's database. Operaton gets a database of its own.
 * This class builds the datasource, and {@code application-operaton.yaml} names it as the one
 * of the adapter {@code operaton}.
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
@Configuration(proxyBeanMethods = false)
@Profile("operaton")
public class Camunda7NextToOperaton {

  /**
   * @return The switch to the older FEEL implementation, for every Camunda 7 engine
   */
  @Bean
  public Camunda7EngineCustomizer camunda7WithoutTheFeelLibrary() {

    return new Camunda7EngineCustomizer() {

      @Override
      public void customize(
          final String adapterId,
          final ProcessEngineConfigurationImpl configuration) {

        configuration.setDmnFeelEnableLegacyBehavior(true);

      }

    };

  }

  /**
   * Not a candidate for injection by type, so the application's own datasource stays the one
   * everything else uses.
   *
   * @param url The address of Operaton's database
   * @return The datasource of Operaton's engine
   */
  @Bean(defaultCandidate = false)
  public DataSource operatonDataSource(
      @Value("${blueprint.operaton-datasource.url}") final String url) {

    return DataSourceBuilder
        .create()
        .url(url)
        .username("sa")
        .password("")
        .build();

  }

}
