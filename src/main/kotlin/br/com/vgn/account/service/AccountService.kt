package br.com.vgn.account.service

import br.com.vgn.account.consumer.AccountMessagePayload
import br.com.vgn.account.domain.AccountEntity
import br.com.vgn.account.domain.AccountStatus
import br.com.vgn.account.repository.AccountRepository
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

@ApplicationScoped
class AccountService(
    private val accountRepository: AccountRepository,
    private val tracer: Tracer,
) {

    private val log: Logger = Logger.getLogger(AccountService::class.java.name)

    @Transactional
    fun createAccountIfNotExists(payload: AccountMessagePayload) {
        val span = tracer.spanBuilder("account.create").startSpan()

        try {
            span.makeCurrent().use {
                val message = payload.account
                val accountId = UUID.fromString(message.id)

                span.setAttribute("account.id", accountId.toString())
                span.setAttribute("account.owner.id", message.owner)
                span.setAttribute("account.status", message.status)

                val existing = accountRepository.findById(accountId)
                if (existing != null) {
                    span.setAttribute("account.already.exists", true)
                    log.infof("Account with id: %s already exists", accountId)
                    return
                }

                val createdAt = Instant.ofEpochSecond(message.createdAt.toLong())
                    .atOffset(ZoneOffset.UTC)

                val account = AccountEntity(
                    id = accountId,
                    ownerId = UUID.fromString(message.owner),
                    balance = BigDecimal.ZERO,
                    status = AccountStatus.valueOf(message.status),
                    createdAt = createdAt,
                    updatedAt = createdAt
                )

                accountRepository.save(account)

                span.setAttribute("account.created", true)
                span.setStatus(StatusCode.OK)

                log.infof("Account with id: %s created", accountId)
            }
        } catch (ex: Exception) {
            span.recordException(ex)
            span.setStatus(StatusCode.ERROR)
            throw ex
        } finally {
            span.end()
        }
    }
}