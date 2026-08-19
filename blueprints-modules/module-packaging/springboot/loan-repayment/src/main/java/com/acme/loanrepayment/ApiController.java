package com.acme.loanrepayment;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * The API of the second use case, GET requests only, so the process can be walked through
 * in a browser.
 *
 * <p>
 * The path starts with the module's own id, as the other module's does with its own. Two
 * modules on one HTTP port is the same kind of collision as two modules on one classpath,
 * and it is avoided the same way: by giving each of them a namespace of its own.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-repayment")
public class ApiController {

  @Autowired
  private Service service;

  /**
   * Starts a repayment.
   *
   * @param customerId The customer paying back.
   * @param amount     The amount owed.
   * @return The id of the repayment started.
   */
  @GetMapping("/start")
  public String start(
      @RequestParam(defaultValue = "C-1002") final String customerId,
      @RequestParam(defaultValue = "6000") final int amount) {

    final var repaymentId = UUID.randomUUID().toString();

    service.initiateRepayment(repaymentId, customerId, amount);

    log.info(
        "Show the result -> http://localhost:8080/api/loan-repayment/{}",
        repaymentId);

    return repaymentId;

  }

  /**
   * Shows what the process did.
   *
   * @param repaymentId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @GetMapping("/{repaymentId}")
  public String show(
      @PathVariable final String repaymentId) {

    return service
        .getRepayment(repaymentId)
        .map(Object::toString)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

}
