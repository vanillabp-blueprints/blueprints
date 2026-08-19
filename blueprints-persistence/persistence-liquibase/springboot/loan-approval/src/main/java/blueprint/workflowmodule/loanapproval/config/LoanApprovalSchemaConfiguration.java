package blueprint.workflowmodule.loanapproval.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import liquibase.integration.spring.SpringLiquibase;

/**
 * The workflow module applies its own changelog, into bookkeeping tables of its own.
 *
 * <p>
 * A workflow module is a JAR which several applications may use, and it owns the tables
 * of its workflow aggregate. If it wrote into the application's
 * {@code DATABASECHANGELOG}, both sides would share one history: the application could no
 * longer upgrade the module without also having its own changelog at hand, and two
 * modules in one application would fight over the same rows. With a table per owner, each
 * side upgrades on its own.
 * </p>
 *
 * <p>
 * The class is named after the workflow module, like everything else the module brings
 * along: the application has a schema configuration of its own, and two classes of the
 * same simple name in one component scan end the boot with a conflicting bean definition.
 * </p>
 *
 * <p>
 * Declaring any {@link SpringLiquibase} bean makes Spring Boot's own Liquibase
 * auto-configuration step aside, so {@code spring.liquibase.*} has no effect in this
 * application - every changelog is wired explicitly, here and in the application module.
 * Hibernate still waits for all of them, because Spring Boot lets the entity manager
 * factory depend on every bean of this type.
 * </p>
 */
@Configuration
public class LoanApprovalSchemaConfiguration {

  /**
   * The changelog of this module, applied at startup.
   *
   * @param dataSource The data source the module's aggregates live in
   * @return The Liquibase instance of the module
   */
  @Bean
  public SpringLiquibase loanApprovalLiquibase(
      final DataSource dataSource) {

    final var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog("classpath:loan-approval/db/changelog.xml");
    // named after the workflow module, like everything else it brings along
    liquibase.setDatabaseChangeLogTable("DATABASECHANGELOG_LOAN_APPROVAL");
    liquibase.setDatabaseChangeLogLockTable("DATABASECHANGELOGLOCK_LOAN_APPROVAL");
    return liquibase;

  }

}
