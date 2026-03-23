package br.com.vgn.account.repository

import br.com.vgn.account.domain.AccountEntity
import java.util.UUID

interface AccountRepository {

    fun findById(id: UUID): AccountEntity?
    fun findByIdWithPessimisticLock(id: UUID): AccountEntity?
    fun save(account: AccountEntity)
}