package br.com.vgn.common.http

object HttpHeaders {

    // Idempotência
    const val IDEMPOTENCY_KEY = "Idempotency-Key"
    // Observabilidade / tracing
    const val CORRELATION_ID = "X-Correlation-Id"
    const val REQUEST_ID = "X-Request-Id"
}