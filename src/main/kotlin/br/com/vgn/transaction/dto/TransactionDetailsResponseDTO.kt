package br.com.vgn.transaction.dto

class TransactionDetailsResponseDTO (
    val id: String,
    val type: String,
    val amount: AmountDTO,
    val status: String,
    val timestamp: String
)