package blueprint.workflowmodule;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The database of Operaton's engine during a migration from Camunda 7 to Operaton. Both
 * engines run inside this application, and two engines must not share one set of tables.
 * Camunda 7 keeps the tables it had before the migration, in the application's database.
 * Operaton gets a database of its own, and {@code application-operaton.yaml} names this bean as
 * the datasource of the adapter {@code operaton}. Only the profile {@code operaton} loads this
 * class.
 */
@Configuration(proxyBeanMethods = false)
@Profile("operaton")
public class OperatonDatabase {

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
