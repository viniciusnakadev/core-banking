package br.com.vgn.transaction.controller

import br.com.vgn.common.http.HttpHeaders
import br.com.vgn.common.http.RequestContext
import br.com.vgn.transaction.dto.TransactionRequestDTO
import br.com.vgn.transaction.service.TransactionService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/transactions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class TransactionController(
    private val transactionService: TransactionService
) {

    @POST
    fun transfer(
        @HeaderParam(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String?,
        @HeaderParam(HttpHeaders.CORRELATION_ID) correlationId: String?,
        request: TransactionRequestDTO
    ): Response {
        if (idempotencyKey.isNullOrBlank()) {
            throw BadRequestException("Header Idempotency-Key is required")
        }

        val finalCorrelationId = correlationId ?: UUID.randomUUID().toString()

        val requestContext = RequestContext(
            correlationId = finalCorrelationId,
            idempotencyKey = idempotencyKey
        )

        val response = transactionService.transfer(requestContext, request)
        return Response.ok(response).build()
    }
}