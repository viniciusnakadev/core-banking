package br.com.vgn.transaction.repository

import br.com.vgn.transaction.repository.FinancialTransactionRepository
import br.com.vgn.transaction.domain.FinancialTransactionEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class FinancialTransactionRepositoryImpl : PanacheRepositoryBase<FinancialTransactionEntity, UUID>, FinancialTransactionRepository {

    override fun findByIdempotencyKey(idempotencyKey: String): FinancialTransactionEntity? {
        return find("idempotencyKey", idempotencyKey).firstResult()
    }

    override fun save(transaction: FinancialTransactionEntity) {
        persist(transaction)
    }
}