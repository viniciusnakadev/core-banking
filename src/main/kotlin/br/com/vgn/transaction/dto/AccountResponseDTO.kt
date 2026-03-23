package br.com.vgn.transaction.dto

import br.com.vgn.transaction.dto.BalanceResponseDTO

class AccountResponseDTO (
    val id: String,
    val balance: BalanceResponseDTO
)