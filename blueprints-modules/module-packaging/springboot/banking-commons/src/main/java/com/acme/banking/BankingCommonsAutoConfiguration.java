package com.acme.banking;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * The library wires itself, for the same reason the workflow modules do: its package is not
 * below the application's package, so nothing scans it.
 *
 * <p>
 * It is registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which is the file Spring Boot reads to find auto-configurations of every JAR on the
 * classpath. {@code @ConditionalOnMissingBean} leaves the application the last word: a
 * project which wants a real client for the directory defines its own bean, and this one
 * steps aside.
 * </p>
 */
@AutoConfiguration
public class BankingCommonsAutoConfiguration {

  /**
   * The directory client both workflow modules use.
   *
   * @return The bean.
   */
  @Bean
  @ConditionalOnMissingBean
  public CustomerDirectory customerDirectory() {

    return new CustomerDirectory();

  }

}
