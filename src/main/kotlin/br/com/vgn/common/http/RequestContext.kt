package br.com.vgn.common.http

data class RequestContext(
    val correlationId: String?,
    val idempotencyKey: String?
)