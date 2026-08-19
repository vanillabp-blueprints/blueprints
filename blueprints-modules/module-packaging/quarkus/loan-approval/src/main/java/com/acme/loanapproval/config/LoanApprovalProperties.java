package com.acme.loanapproval.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-approval")
public interface LoanApprovalProperties {

  /**
   * The highest credit rating the rating step may award.
   *
   * @return The rating scale.
   */
  @WithDefault("100")
  int ratingScale();

  /**
   * Which rating provider the module talks to. A value that really differs per environment,
   * which is why it lives in the module's profile files rather than in the application's.
   *
   * @return The provider's name.
   */
  @WithDefault("internal")
  String ratingProvider();

}
