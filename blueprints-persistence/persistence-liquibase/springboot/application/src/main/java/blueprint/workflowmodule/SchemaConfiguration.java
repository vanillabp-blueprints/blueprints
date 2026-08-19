package blueprint.workflowmodule;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import liquibase.integration.spring.SpringLiquibase;

/**
 * The application applies the changelog of everything which is not a workflow module: the
 * tables VanillaBP needs, the outbox table of the Spring Boot integration and, with an
 * embedded engine, the engine's own tables.
 *
 * <p>
 * Which changelog that is depends on the engine, so the file is named in the profile of
 * the engine rather than here: a remote engine has no tables, an embedded Camunda 7 has
 * plenty and ships them itself. Everything else about the two runs is the same.
 * </p>
 *
 * <p>
 * This instance keeps Liquibase's default bookkeeping tables, {@code DATABASECHANGELOG}
 * and {@code DATABASECHANGELOGLOCK}. They are the application's, and the workflow module
 * has its own next to them.
 * </p>
 */
@Configuration
public class SchemaConfiguration {

  /**
   * The application's changelog, applied at startup and before anything reads a table:
   * Hibernate waits for every {@link SpringLiquibase} bean, and VanillaBP checks for its
   * tables once all beans exist.
   *
   * @param dataSource The data source of the application
   * @param changeLog The changelog to apply, named by the engine's profile
   * @return The Liquibase instance of the application
   */
  @Bean
  public SpringLiquibase applicationLiquibase(
      final DataSource dataSource,
      @Value("${blueprint.schema.changelog}") final String changeLog) {

    final var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changeLog);
    return liquibase;

  }

}
