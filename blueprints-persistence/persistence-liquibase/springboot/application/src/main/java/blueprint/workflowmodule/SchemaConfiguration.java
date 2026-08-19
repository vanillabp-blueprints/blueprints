package blueprint.workflowmodule;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import liquibase.integration.spring.SpringLiquibase;

/**
 * The application applies one changelog while it starts, and that changelog includes
 * everything the schema consists of: the tables VanillaBP needs, the outbox table of the
 * Spring Boot integration, the changelog of the workflow module and, with an embedded engine,
 * the engine's own.
 *
 * <p>
 * Which changelog that is depends on the engine, so the file is named in the profile of
 * the engine rather than here: a remote engine has no tables, an embedded Camunda 7 has
 * plenty and ships them itself. Everything else about the two runs is the same.
 * </p>
 *
 * <p>
 * One run, one bookkeeping table. A workflow module still owns its tables, because Liquibase
 * records a changeset under the logical path its changelog declares rather than under the file
 * which included it. So the module's rows in {@code DATABASECHANGELOG} stay recognizable and a
 * later version of the module finds its own history, without a second Liquibase instance and
 * without tables of its own.
 * </p>
 *
 * <p>
 * A bean of type {@link SpringLiquibase} makes Spring Boot's own Liquibase auto-configuration
 * step aside, so {@code spring.liquibase.*} has no effect here. The entity manager factory
 * still waits for it, because Spring Boot's Liquibase module lets it depend on every bean of
 * this type.
 * </p>
 */
@Configuration
public class SchemaConfiguration {

  /**
   * The changelog, applied at startup and before anything reads a table: Hibernate waits for
   * every {@link SpringLiquibase} bean, and VanillaBP checks for its tables once all beans
   * exist.
   *
   * @param dataSource The data source of the application
   * @param changeLog The changelog to apply, named by the engine's profile
   * @return The Liquibase instance applying the schema
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
