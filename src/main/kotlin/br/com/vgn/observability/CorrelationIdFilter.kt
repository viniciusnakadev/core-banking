package br.com.vgn.observability

import br.com.vgn.common.http.HttpHeaders
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.MDC
import java.util.UUID

@Provider
@Priority(Priorities.AUTHENTICATION)
class CorrelationIdFilter : ContainerRequestFilter, ContainerResponseFilter {

    companion object {
        const val MDC_KEY = "correlationId"
        const val REQUEST_PROPERTY = "correlationId"
    }

    override fun filter(requestContext: ContainerRequestContext) {
        val correlationId = requestContext.getHeaderString(HttpHeaders.CORRELATION_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        MDC.put(MDC_KEY, correlationId)
        requestContext.setProperty(REQUEST_PROPERTY, correlationId)
    }

    override fun filter(
        requestContext: ContainerRequestContext,
        responseContext: ContainerResponseContext
    ) {
        val correlationId = requestContext.getProperty(REQUEST_PROPERTY)?.toString()

        if (!correlationId.isNullOrBlank()) {
            responseContext.headers.add(HttpHeaders.CORRELATION_ID, correlationId)
        }

        MDC.remove(MDC_KEY)
    }
}