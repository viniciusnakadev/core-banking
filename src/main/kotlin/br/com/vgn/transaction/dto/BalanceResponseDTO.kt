package br.com.vgn.transaction.dto

import java.math.BigDecimal

class BalanceResponseDTO (
    val amount: BigDecimal,
    val currency: String
)