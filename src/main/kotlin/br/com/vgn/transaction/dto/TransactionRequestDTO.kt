package br.com.vgn.transaction.dto

import br.com.vgn.transaction.domain.TransactionType
import java.util.UUID

class TransactionRequestDTO (
    val accountId: UUID,
    val type: TransactionType,
    val amount: AmountDTO
)
