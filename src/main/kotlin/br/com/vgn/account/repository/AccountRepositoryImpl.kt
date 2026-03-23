package br.com.vgn.account.repository

import br.com.vgn.account.domain.AccountEntity
import br.com.vgn.account.repository.AccountRepository
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@ApplicationScoped
class AccountRepositoryImpl : PanacheRepositoryBase<AccountEntity, UUID>, AccountRepository {

    override fun findById(id: UUID): AccountEntity? {
        return find("id", id).firstResult();
    }

    override fun findByIdWithPessimisticLock(id: UUID): AccountEntity? {
        return find("id", id)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult();
    }

    override fun save(account: AccountEntity) {
        persist(account)
    }
}