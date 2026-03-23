package br.com.vgn.transaction.dto

import java.math.BigDecimal

data class AmountDTO(
    val value: BigDecimal,
    val currency: String
)