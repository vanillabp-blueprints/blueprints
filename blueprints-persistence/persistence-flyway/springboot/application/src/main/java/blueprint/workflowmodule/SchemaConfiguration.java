package blueprint.workflowmodule;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One Flyway per owner of schema, each with a migration history of its own.
 *
 * <p>
 * Flyway knows one timeline per history table and nothing else. It has no notion of who wrote a
 * migration, so two owners in one history compete for version numbers: VanillaBP ships
 * {@code V2.0.0}, and the day an application picks that number for its own migration, one of the
 * two loses. Separate history tables are therefore not tidiness, they are what keeps the
 * timelines from colliding, and each side can be upgraded without touching the other.
 * </p>
 *
 * <p>
 * Three owners exist here: VanillaBP, whose SQL comes out of
 * {@code io.vanillabp:vanillabp-schema}; the workflow module, whose migrations travel in its own
 * JAR; and the application itself, which owns what the outbox library needs and, with an embedded
 * engine, the engine's tables.
 * </p>
 *
 * <p>
 * Two settings are not decoration. {@code baselineOnMigrate} is what lets an instance work in a
 * schema which is not empty, and next to another history that is always the case: without it
 * Flyway sees tables it has no history for and refuses to touch anything. And
 * {@code baselineVersion("0")} goes with it, because the default baseline is version 1, which
 * would silently skip every migration numbered 1.0.0.
 * </p>
 *
 * <p>
 * Every instance is paired with a {@link FlywayMigrationInitializer}, and that is what makes the
 * schema exist before anything reads it: Spring Boot's Flyway module lets the entity manager
 * factory depend on beans of that type. A {@link Flyway} bean alone would migrate whenever its
 * bean happened to be created.
 * </p>
 */
@Configuration
public class SchemaConfiguration {

  /** VanillaBP's own SQL, out of the artifact, under a history table of its own. */
  @Bean
  public Flyway vanillaBpFlyway(
      final DataSource dataSource,
      @Value("${blueprint.schema.vanillabp-location}") final String location) {

    return Flyway
        .configure()
        .dataSource(dataSource)
        .locations(location)
        .table("flyway_schema_history_vanillabp")
        // see the class comment: both are needed for a history next to another one
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load();

  }

  /**
   * @param vanillaBpFlyway The instance to run
   * @return The hook which runs it while the context is built
   */
  @Bean
  public FlywayMigrationInitializer vanillaBpFlywayInitializer(
      final Flyway vanillaBpFlyway) {

    return new FlywayMigrationInitializer(vanillaBpFlyway);

  }

  /** The workflow module's migrations, out of its JAR, under a history table named after it. */
  @Bean
  public Flyway loanApprovalFlyway(
      final DataSource dataSource) {

    return Flyway
        .configure()
        .dataSource(dataSource)
        .locations("classpath:loan-approval/db/migration")
        .table("flyway_schema_history_loan_approval")
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load();

  }

  /**
   * @param loanApprovalFlyway The instance to run
   * @return The hook which runs it while the context is built
   */
  @Bean
  public FlywayMigrationInitializer loanApprovalFlywayInitializer(
      final Flyway loanApprovalFlyway) {

    return new FlywayMigrationInitializer(loanApprovalFlyway);

  }

  /**
   * What the application owns: the table of the outbox library and, with an embedded engine, the
   * engine's own tables. Which locations those are depends on the engine, so they are named in
   * the profile of the engine.
   *
   * @param dataSource The data source of the application
   * @param locations The migrations to apply, named by the engine's profile
   * @return The application's own Flyway instance
   */
  @Bean
  public Flyway applicationFlyway(
      final DataSource dataSource,
      @Value("${blueprint.schema.application-locations}") final String[] locations) {

    return Flyway
        .configure()
        .dataSource(dataSource)
        .locations(locations)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load();

  }

  /**
   * @param applicationFlyway The instance to run
   * @return The hook which runs it while the context is built
   */
  @Bean
  public FlywayMigrationInitializer applicationFlywayInitializer(
      final Flyway applicationFlyway) {

    return new FlywayMigrationInitializer(applicationFlyway);

  }

}
