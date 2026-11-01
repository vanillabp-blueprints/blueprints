package blueprint.workflowmodule;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The database of CIB seven's engine during a migration from Camunda 7 to CIB seven. Both
 * engines run inside this application, and two engines must not share one set of tables.
 * Camunda 7 keeps the tables it had before the migration, in the application's database.
 * CIB seven gets a database of its own, and {@code application-cibseven.yaml} names this bean as
 * the datasource of the adapter {@code cibseven}. Only the profile {@code cibseven} loads this
 * class.
 */
@Configuration(proxyBeanMethods = false)
@Profile("cibseven")
public class CibSevenDatabase {

  /**
   * Not a candidate for injection by type, so the application's own datasource stays the one
   * everything else uses.
   *
   * @param url The address of CIB seven's database
   * @return The datasource of CIB seven's engine
   */
  @Bean(defaultCandidate = false)
  public DataSource cibsevenDataSource(
      @Value("${blueprint.cibseven-datasource.url}") final String url) {

    return DataSourceBuilder
        .create()
        .url(url)
        .username("sa")
        .password("")
        .build();

  }

}
