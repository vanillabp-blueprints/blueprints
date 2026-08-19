package com.acme.loanrepayment;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
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
@ApplicationScoped
@Path("/api/loan-repayment")
public class ApiController {

  @Inject
  Service service;

  /**
   * Starts a repayment.
   *
   * @param customerId The customer paying back.
   * @param amount     The amount owed.
   * @return The id of the repayment started.
   */
  @GET
  @Path("/start")
  public String start(
      @QueryParam("customerId")
      @DefaultValue("C-1002") final String customerId,
      @QueryParam("amount")
      @DefaultValue("6000") final int amount) {

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
  @GET
  @Path("/{repaymentId}")
  public String show(
      @PathParam("repaymentId") final String repaymentId) {

    return service
        .getRepayment(repaymentId)
        .map(Object::toString)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

}
