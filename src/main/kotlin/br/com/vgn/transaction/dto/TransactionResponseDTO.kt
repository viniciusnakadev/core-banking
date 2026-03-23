package br.com.vgn.transaction.dto

class TransactionResponseDTO (
    val transaction: TransactionDetailsResponseDTO,
    val account: AccountResponseDTO
)