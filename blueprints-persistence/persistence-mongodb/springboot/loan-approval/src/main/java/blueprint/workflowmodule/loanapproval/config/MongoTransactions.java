package blueprint.workflowmodule.loanapproval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * The transaction manager of this workflow module's persistence, and the one class this
 * blueprint has beyond the base blueprint.
 *
 * <p>
 * With a relational database the platform contributes a transaction manager on its own, so
 * nobody writes this class. For MongoDB it does not, deliberately: a MongoDB transaction
 * needs a replica set, and a platform cannot know whether the deployment is one. So the
 * application says it, and VanillaBP asks for it at startup rather than assuming it.
 * </p>
 *
 * <p>
 * Everything VanillaBP does around a workflow aggregate then runs inside this manager's
 * transaction: loading the aggregate, the {@code @WorkflowTask} method, saving the aggregate,
 * the entry in the phase-two outbox and the record about the delivered task either all commit
 * or none of them do. Without the manager the application does not start, and the message
 * names this class as one of the ways out.
 * </p>
 *
 * <p>
 * It sits in the workflow module because the module is what brings the persistence along. An
 * application may define it instead, and it has to when it holds several workflow modules on
 * different databases - then each aggregate is attributed to its transaction through a
 * {@code TransactionRunnerAware} bean.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Configuration
public class MongoTransactions {

  /**
   * The transaction manager MongoDB needs and Spring Boot does not define.
   *
   * @param databaseFactory The factory the platform configured from
   *          {@code spring.data.mongodb.*}.
   * @return The transaction manager.
   */
  @Bean
  public MongoTransactionManager mongoTransactionManager(
      final MongoDatabaseFactory databaseFactory) {

    return new MongoTransactionManager(databaseFactory);

  }

}
