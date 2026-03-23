package transaction.unit

import br.com.vgn.common.http.RequestContext

import br.com.vgn.account.domain.AccountEntity
import br.com.vgn.account.domain.AccountStatus
import br.com.vgn.account.repository.AccountRepository
import br.com.vgn.transaction.repository.FinancialTransactionRepository
import br.com.vgn.transaction.domain.FinancialTransactionEntity
import br.com.vgn.transaction.domain.TransactionStatus
import br.com.vgn.transaction.domain.TransactionType
import br.com.vgn.transaction.dto.AmountDTO
import br.com.vgn.transaction.dto.TransactionRequestDTO
import br.com.vgn.transaction.service.TransactionService
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

class TransactionServiceTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var financialTransactionRepository: FinancialTransactionRepository
    private lateinit var tracer: Tracer
    private lateinit var spanBuilder: SpanBuilder
    private lateinit var span: Span

    private lateinit var transactionService: TransactionService

    private fun requestContext(idempotencyKey: String): RequestContext {
        return RequestContext(
            correlationId = "test-correlation-id",
            idempotencyKey = idempotencyKey
        )
    }

    @BeforeEach
    fun setup() {
        accountRepository = mock()
        financialTransactionRepository = mock()
        tracer = mock()
        spanBuilder = mock()
        span = mock()

        whenever(tracer.spanBuilder(any())).thenReturn(spanBuilder)
        whenever(spanBuilder.startSpan()).thenReturn(span)

        transactionService = TransactionService(
            accountRepository,
            financialTransactionRepository,
            tracer
        )
    }

    @Test
    fun `deve processar CREDIT com sucesso`() {
        val accountId = UUID.randomUUID()
        val account = buildAccount(accountId, BigDecimal("100.00"))

        val request = TransactionRequestDTO(
            accountId = accountId,
            type = TransactionType.CREDIT,
            amount = AmountDTO(
                value = BigDecimal("50.00"),
                currency = "BRL"
            )
        )

        whenever(accountRepository.findByIdWithPessimisticLock(accountId)).thenReturn(account)
        whenever(financialTransactionRepository.findByIdempotencyKey("idem-1")).thenReturn(null)

        val response = transactionService.transfer(requestContext("idem-1"), request)

        assertEquals("SUCCEEDED", response.transaction.status)
        assertEquals(BigDecimal("150.00"), response.account.balance.amount)
        assertEquals("BRL", response.account.balance.currency)

        val transactionCaptor = argumentCaptor<FinancialTransactionEntity>()
        verify(financialTransactionRepository).save(transactionCaptor.capture())
        assertEquals(TransactionStatus.SUCCEEDED, transactionCaptor.firstValue.status)
        assertEquals(TransactionType.CREDIT, transactionCaptor.firstValue.type)
        assertEquals(BigDecimal("50.00"), transactionCaptor.firstValue.amountValue)
    }

    @Test
    fun `deve processar DEBIT com sucesso quando houver saldo`() {
        val accountId = UUID.randomUUID()
        val account = buildAccount(accountId, BigDecimal("100.00"))

        val request = TransactionRequestDTO(
            accountId = accountId,
            type = TransactionType.DEBIT,
            amount = AmountDTO(
                value = BigDecimal("30.00"),
                currency = "BRL"
            )
        )

        whenever(accountRepository.findByIdWithPessimisticLock(accountId)).thenReturn(account)
        whenever(financialTransactionRepository.findByIdempotencyKey("idem-2")).thenReturn(null)

        val response = transactionService.transfer(requestContext("idem-2"), request)

        assertEquals("SUCCEEDED", response.transaction.status)
        assertEquals(BigDecimal("70.00"), response.account.balance.amount)

        val transactionCaptor = argumentCaptor<FinancialTransactionEntity>()
        verify(financialTransactionRepository).save(transactionCaptor.capture())
        assertEquals(TransactionStatus.SUCCEEDED, transactionCaptor.firstValue.status)
        assertEquals(TransactionType.DEBIT, transactionCaptor.firstValue.type)
        assertEquals(BigDecimal("30.00"), transactionCaptor.firstValue.amountValue)
    }

    @Test
    fun `deve retornar FAILED em DEBIT sem saldo suficiente e manter saldo`() {
        val accountId = UUID.randomUUID()
        val account = buildAccount(accountId, BigDecimal("20.00"))

        val request = TransactionRequestDTO(
            accountId = accountId,
            type = TransactionType.DEBIT,
            amount = AmountDTO(
                value = BigDecimal("50.00"),
                currency = "BRL"
            )
        )

        whenever(accountRepository.findByIdWithPessimisticLock(accountId)).thenReturn(account)
        whenever(financialTransactionRepository.findByIdempotencyKey("idem-3")).thenReturn(null)

        val response = transactionService.transfer(requestContext("idem-3"), request)

        assertEquals("FAILED", response.transaction.status)
        assertEquals(BigDecimal("20.00"), response.account.balance.amount)

        val transactionCaptor = argumentCaptor<FinancialTransactionEntity>()
        verify(financialTransactionRepository).save(transactionCaptor.capture())
        assertEquals(TransactionStatus.FAILED, transactionCaptor.firstValue.status)
        assertEquals(TransactionType.DEBIT, transactionCaptor.firstValue.type)
        assertEquals(BigDecimal("50.00"), transactionCaptor.firstValue.amountValue)
    }

    @Test
    fun `deve lançar erro quando amount for menor ou igual a zero`() {
        val request = TransactionRequestDTO(
            accountId = UUID.randomUUID(),
            type = TransactionType.CREDIT,
            amount = AmountDTO(
                value = BigDecimal.ZERO,
                currency = "BRL"
            )
        )

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.transfer(requestContext("idem-4"), request)
        }

        assertTrue(ex.message!!.contains("Amount"))
        verifyNoInteractions(financialTransactionRepository)
    }

    @Test
    fun `deve lançar erro quando currency for invalida`() {
        val request = TransactionRequestDTO(
            accountId = UUID.randomUUID(),
            type = TransactionType.CREDIT,
            amount = AmountDTO(
                value = BigDecimal("10.00"),
                currency = "ABC"
            )
        )

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.transfer(requestContext("idem-5"), request)
        }

        assertTrue(ex.message!!.contains("Invalid ISO4217"))
        verifyNoInteractions(financialTransactionRepository)
    }

    @Test
    fun `deve lançar NotFoundException quando conta nao existir`() {
        val accountId = UUID.randomUUID()

        val request = TransactionRequestDTO(
            accountId = accountId,
            type = TransactionType.CREDIT,
            amount = AmountDTO(
                value = BigDecimal("10.00"),
                currency = "BRL"
            )
        )

        whenever(financialTransactionRepository.findByIdempotencyKey("idem-6")).thenReturn(null)
        whenever(accountRepository.findByIdWithPessimisticLock(accountId)).thenReturn(null)

        assertThrows<NotFoundException> {
            transactionService.transfer(requestContext("idem-6"), request)
        }

        verify(financialTransactionRepository).findByIdempotencyKey("idem-6")
        verifyNoMoreInteractions(financialTransactionRepository)
    }

    @Test
    fun `deve retornar transacao existente quando idempotency key for repetida com mesmo payload`() {
        val accountId = UUID.randomUUID()
        val account = buildAccount(accountId, BigDecimal("120.00"))

        val existing = FinancialTransactionEntity(
            id = UUID.randomUUID(),
            account = account,
            type = TransactionType.CREDIT,
            amountValue = BigDecimal("20.00"),
            amountCurrency = "BRL",
            status = TransactionStatus.SUCCEEDED,
            idempotencyKey = "idem-7",
            requestHash = generateHashForTest(
                accountId,
                TransactionType.CREDIT,
                BigDecimal("20.00"),
                "BRL"
            ),
            createdAt = OffsetDateTime.now()
        )

        val request = TransactionRequestDTO(
            accountId = accountId,
            type = TransactionType.CREDIT,
            amount = AmountDTO(
                value = BigDecimal("20.00"),
                currency = "BRL"
            )
        )

        whenever(financialTransactionRepository.findByIdempotencyKey("idem-7")).thenReturn(existing)

        val response = transactionService.transfer(requestContext("idem-7"), request)

        assertEquals(existing.id.toString(), response.transaction.id)
        verify(financialTransactionRepository).findByIdempotencyKey("idem-7")
        verifyNoMoreInteractions(financialTransactionRepository)

        verifyNoInteractions(accountRepository)
    }

    @Test
    fun `deve lançar conflito quando idempotency key for repetida com payload diferente`() {
        val accountId = UUID.randomUUID()
        val account = buildAccount(accountId, BigDecimal("120.00"))

        val existing = FinancialTransactionEntity(
            id = UUID.randomUUID(),
            account = account,
            type = TransactionType.CREDIT,
            amountValue = BigDecimal("20.00"),
            amountCurrency = "BRL",
            status = TransactionStatus.SUCCEEDED,
            idempotencyKey = "idem-8",
            requestHash = "hash-antigo",
            createdAt = OffsetDateTime.now()
        )

        val request = TransactionRequestDTO(
            accountId = accountId,
            type = TransactionType.CREDIT,
            amount = AmountDTO(
                value = BigDecimal("99.00"),
                currency = "BRL"
            )
        )

        whenever(financialTransactionRepository.findByIdempotencyKey("idem-8")).thenReturn(existing)

        val ex = assertThrows<WebApplicationException> {
            transactionService.transfer(requestContext("idem-8"), request)
        }

        assertEquals(409, ex.response.status)
        verify(financialTransactionRepository).findByIdempotencyKey("idem-8")
        verifyNoMoreInteractions(financialTransactionRepository)
    }

    private fun buildAccount(id: UUID, balance: BigDecimal): AccountEntity {
        return AccountEntity(
            id = id,
            ownerId = UUID.randomUUID(),
            balance = balance,
            status = AccountStatus.ENABLED,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now()
        )
    }

    private fun generateHashForTest(
        accountId: UUID,
        type: TransactionType,
        value: BigDecimal,
        currency: String
    ): String {
        val raw = buildString {
            append(accountId)
            append("|")
            append(type.name)
            append("|")
            append(value.stripTrailingZeros().toPlainString())
            append("|")
            append(currency.uppercase())
        }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }
}