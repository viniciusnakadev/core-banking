package br.com.vgn.observability

import org.jboss.logging.MDC

object CorrelationIdHolder {
    fun get(): String? = MDC.get("correlationId")?.toString()
}