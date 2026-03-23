package br.com.vgn.transaction.service

import br.com.vgn.account.repository.AccountRepository
import br.com.vgn.common.http.RequestContext
import br.com.vgn.transaction.domain.FinancialTransactionEntity
import br.com.vgn.transaction.domain.TransactionStatus
import br.com.vgn.transaction.domain.TransactionType
import br.com.vgn.transaction.dto.AccountResponseDTO
import br.com.vgn.transaction.dto.AmountDTO
import br.com.vgn.transaction.dto.BalanceResponseDTO
import br.com.vgn.transaction.dto.TransactionDetailsResponseDTO
import br.com.vgn.transaction.dto.TransactionRequestDTO
import br.com.vgn.transaction.dto.TransactionResponseDTO
import br.com.vgn.transaction.repository.FinancialTransactionRepository
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Currency
import java.util.UUID

@ApplicationScoped
class TransactionService(
    private val accountRepository: AccountRepository,
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val tracer: Tracer
) {

    private val log = Logger.getLogger(TransactionService::class.java)

    @Transactional
    fun transfer(requestContext: RequestContext, request: TransactionRequestDTO): TransactionResponseDTO {
        val span = tracer.spanBuilder("transaction.transfer").startSpan()

        try {
            span.makeCurrent().use {
                val correlationId = requestContext.correlationId ?: "N/A"
                val idempotencyKey = requestContext.idempotencyKey
                    ?: throw IllegalArgumentException("Idempotency-Key must not be null")
                val normalizedCurrency = request.amount.currency.uppercase()

                span.setAttribute("correlation.id", correlationId)
                span.setAttribute("idempotency.key", idempotencyKey)
                span.setAttribute("transaction.type", request.type.name)
                span.setAttribute("transaction.amount.value", request.amount.value.toDouble())
                span.setAttribute("transaction.amount.currency", normalizedCurrency)
                span.setAttribute("account.id", request.accountId.toString())

                validateRequest(request)

                val requestHash = generateRequestHash(request)
                span.setAttribute("transaction.request.hash", requestHash)

                val existingTransaction = financialTransactionRepository.findByIdempotencyKey(idempotencyKey)

                if (existingTransaction != null) {
                    if (existingTransaction.requestHash != requestHash) {
                        span.setAttribute("idempotency.reused_with_different_payload", true)
                        throw WebApplicationException(
                            "Idempotency-Key already used with different payload",
                            Response.Status.CONFLICT
                        )
                    }

                    span.setAttribute("idempotency.replay", true)
                    span.setAttribute("transaction.id", existingTransaction.id.toString())
                    span.setAttribute("transaction.status", existingTransaction.status!!.name)
                    span.setAttribute("account.balance.current", existingTransaction.account!!.balance.toDouble())
                    span.setStatus(StatusCode.OK)

                    log.infof(
                        "Idempotent replay detected. correlationId=%s idempotencyKey=%s transactionId=%s",
                        correlationId,
                        idempotencyKey,
                        existingTransaction.id
                    )

                    return toResponse(existingTransaction)
                }

                val account = accountRepository.findByIdWithPessimisticLock(request.accountId)
                    ?: throw NotFoundException("Account not found: ${request.accountId}")

                val now = OffsetDateTime.now()
                val transactionId = UUID.randomUUID()

                val transactionStatus: TransactionStatus

                when (request.type) {
                    TransactionType.CREDIT -> {
                        account.balance = account.balance.add(request.amount.value)
                        transactionStatus = TransactionStatus.SUCCEEDED
                    }

                    TransactionType.DEBIT -> {
                        val newBalance = account.balance.subtract(request.amount.value)

                        if (newBalance < BigDecimal.ZERO) {
                            transactionStatus = TransactionStatus.FAILED
                        } else {
                            account.balance = newBalance
                            transactionStatus = TransactionStatus.SUCCEEDED
                        }
                    }
                }

                val transaction = FinancialTransactionEntity(
                    id = transactionId,
                    account = account,
                    type = request.type,
                    amountValue = request.amount.value,
                    amountCurrency = normalizedCurrency,
                    status = transactionStatus,
                    idempotencyKey = idempotencyKey,
                    requestHash = requestHash,
                    createdAt = now
                )

                financialTransactionRepository.save(transaction)

                span.setAttribute("transaction.id", transaction.id.toString())
                span.setAttribute("transaction.status", transaction.status!!.name)
                span.setAttribute("account.balance.updated", account.balance.toDouble())
                span.setStatus(StatusCode.OK)

                log.infof(
                    "Transaction processed. correlationId=%s idempotencyKey=%s transactionId=%s accountId=%s type=%s status=%s amount=%s currency=%s",
                    correlationId,
                    idempotencyKey,
                    transaction.id,
                    account.id,
                    transaction.type,
                    transaction.status,
                    transaction.amountValue,
                    transaction.amountCurrency
                )

                return toResponse(transaction)
            }
        } catch (ex: Exception) {
            span.recordException(ex)
            span.setStatus(StatusCode.ERROR)
            throw ex
        } finally {
            span.end()
        }
    }

    private fun validateRequest(request: TransactionRequestDTO) {
        if (request.amount.value <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount must be greater than zero")
        }

        try {
            Currency.getInstance(request.amount.currency.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid ISO4217 currency code: ${request.amount.currency}")
        }
    }

    private fun generateRequestHash(request: TransactionRequestDTO): String {
        val raw = buildString {
            append(request.accountId)
            append("|")
            append(request.type.name)
            append("|")
            append(request.amount.value.stripTrailingZeros().toPlainString())
            append("|")
            append(request.amount.currency.uppercase())
        }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun toResponse(transaction: FinancialTransactionEntity): TransactionResponseDTO {
        val account = transaction.account
            ?: throw IllegalStateException("Transaction account must not be null")

        return TransactionResponseDTO(
            transaction = TransactionDetailsResponseDTO(
                id = transaction.id.toString(),
                type = transaction.type!!.name,
                amount = AmountDTO(
                    value = transaction.amountValue,
                    currency = transaction.amountCurrency!!
                ),
                status = transaction.status!!.name,
                timestamp = transaction.createdAt.toString()
            ),
            account = AccountResponseDTO(
                id = account.id.toString(),
                balance = BalanceResponseDTO(
                    amount = account.balance,
                    currency = transaction.amountCurrency!!
                )
            )
        )
    }
}