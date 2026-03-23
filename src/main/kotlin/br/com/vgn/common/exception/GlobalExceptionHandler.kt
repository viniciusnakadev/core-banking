package br.com.vgn.common.exception

import br.com.vgn.common.exception.dto.ErrorResponseDTO
import br.com.vgn.observability.CorrelationIdHolder
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger
import java.time.OffsetDateTime

@Provider
class GlobalExceptionHandler : ExceptionMapper<Throwable> {

    private val log = Logger.getLogger(GlobalExceptionHandler::class.java)

    override fun toResponse(exception: Throwable): Response {
        val correlationId = CorrelationIdHolder.get()
        val timestamp = OffsetDateTime.now().toString()

        return when (exception) {

            is IllegalArgumentException -> {
                val errorCode = when {
                    exception.message?.contains("Amount") == true -> ErrorCode.INVALID_AMOUNT
                    exception.message?.contains("currency") == true -> ErrorCode.INVALID_CURRENCY
                    else -> ErrorCode.BAD_REQUEST
                }

                log.warnf(
                    "Validation error. correlationId=%s message=%s",
                    correlationId,
                    exception.message
                )

                buildResponse(
                    Response.Status.BAD_REQUEST,
                    errorCode,
                    exception.message ?: "Validation error",
                    correlationId,
                    timestamp
                )
            }

            is NotFoundException -> {
                log.warnf(
                    "Not found. correlationId=%s message=%s",
                    correlationId,
                    exception.message
                )

                buildResponse(
                    Response.Status.NOT_FOUND,
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    exception.message ?: "Resource not found",
                    correlationId,
                    timestamp
                )
            }

            is WebApplicationException -> {
                val status = exception.response?.status ?: 500

                val errorCode = when (status) {
                    409 -> ErrorCode.IDEMPOTENCY_CONFLICT
                    else -> ErrorCode.BAD_REQUEST
                }

                log.warnf(
                    "Web exception. correlationId=%s status=%d message=%s",
                    correlationId,
                    status,
                    exception.message
                )

                buildResponse(
                    Response.Status.fromStatusCode(status),
                    errorCode,
                    exception.message ?: "Request error",
                    correlationId,
                    timestamp
                )
            }

            else -> {
                log.errorf(
                    exception,
                    "Internal error. correlationId=%s message=%s",
                    correlationId,
                    exception.message
                )

                buildResponse(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unexpected internal error",
                    correlationId,
                    timestamp
                )
            }
        }
    }

    private fun buildResponse(
        status: Response.Status,
        errorCode: ErrorCode,
        message: String,
        correlationId: String?,
        timestamp: String
    ): Response {
        return Response.status(status)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ErrorResponseDTO(
                    code = errorCode.code,
                    message = message,
                    correlationId = correlationId,
                    timestamp = timestamp
                )
            )
            .build()
    }
}