package br.com.vgn.transaction.repository

import br.com.vgn.transaction.domain.FinancialTransactionEntity

interface FinancialTransactionRepository {
    fun findByIdempotencyKey(idempotencyKey: String): FinancialTransactionEntity?
    fun save(transaction: FinancialTransactionEntity)
}