package br.com.vgn.common.exception.dto

data class ErrorResponseDTO(
    val code: String,
    val message: String,
    val correlationId: String?,
    val timestamp: String
)