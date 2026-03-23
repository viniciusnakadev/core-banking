package account.unit

import br.com.vgn.account.consumer.AccountMessage
import br.com.vgn.account.consumer.AccountMessagePayload
import br.com.vgn.account.domain.AccountEntity
import br.com.vgn.account.domain.AccountStatus
import br.com.vgn.account.repository.AccountRepository
import br.com.vgn.account.service.AccountService
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AccountServiceTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var accountService: AccountService
    private lateinit var tracer: Tracer
    private lateinit var spanBuilder: SpanBuilder
    private lateinit var span: Span

    @BeforeEach
    fun setup() {
        accountRepository = mock()
        tracer = mock()
        spanBuilder = mock()
        span = mock()

        whenever(tracer.spanBuilder(any())).thenReturn(spanBuilder)
        whenever(spanBuilder.startSpan()).thenReturn(span)

        accountService = AccountService(accountRepository, tracer)
    }

    @Test
    fun `deve criar conta quando ela nao existir`() {
        val accountId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val payload = buildPayload(
            id = accountId.toString(),
            owner = ownerId.toString(),
            createdAt = "1749661080",
            status = "ENABLED"
        )

        whenever(accountRepository.findById(accountId)).thenReturn(null)

        accountService.createAccountIfNotExists(payload)

        val captor = argumentCaptor<AccountEntity>()
        verify(accountRepository).findById(accountId)
        verify(accountRepository).save(captor.capture())
        verifyNoMoreInteractions(accountRepository)

        val saved = captor.firstValue
        assertEquals(accountId, saved.id)
        assertEquals(ownerId, saved.ownerId)
        assertEquals(BigDecimal.ZERO, saved.balance)
        assertEquals(AccountStatus.ENABLED, saved.status)

        val expectedCreatedAt = Instant.ofEpochSecond(1749661080).atOffset(ZoneOffset.UTC)
        assertEquals(expectedCreatedAt, saved.createdAt)
        assertEquals(expectedCreatedAt, saved.updatedAt)
    }

    @Test
    fun `nao deve criar conta quando ela ja existir`() {
        val accountId = UUID.randomUUID()
        val existing = AccountEntity(
            id = accountId,
            ownerId = UUID.randomUUID(),
            balance = BigDecimal.ZERO,
            status = AccountStatus.ENABLED,
            createdAt = Instant.now().atOffset(ZoneOffset.UTC),
            updatedAt = Instant.now().atOffset(ZoneOffset.UTC)
        )

        val payload = buildPayload(
            id = accountId.toString(),
            owner = UUID.randomUUID().toString(),
            createdAt = "1749661080",
            status = "ENABLED"
        )

        whenever(accountRepository.findById(accountId)).thenReturn(existing)

        accountService.createAccountIfNotExists(payload)

        verify(accountRepository).findById(accountId)
        verify(accountRepository, never()).save(org.mockito.kotlin.any())
        verifyNoMoreInteractions(accountRepository)
    }

    @Test
    fun `deve lançar erro quando account id for invalido`() {
        val payload = buildPayload(
            id = "id-invalido",
            owner = UUID.randomUUID().toString(),
            createdAt = "1749661080",
            status = "ENABLED"
        )

        assertThrows(IllegalArgumentException::class.java) {
            accountService.createAccountIfNotExists(payload)
        }

        verifyNoMoreInteractions(accountRepository)
    }

    @Test
    fun `deve lançar erro quando owner id for invalido`() {
        val accountId = UUID.randomUUID()

        val payload = buildPayload(
            id = accountId.toString(),
            owner = "owner-invalido",
            createdAt = "1749661080",
            status = "ENABLED"
        )

        whenever(accountRepository.findById(accountId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            accountService.createAccountIfNotExists(payload)
        }

        verify(accountRepository).findById(accountId)
        verify(accountRepository, never()).save(org.mockito.kotlin.any())
        verifyNoMoreInteractions(accountRepository)
    }

    @Test
    fun `deve lançar erro quando created_at for invalido`() {
        val accountId = UUID.randomUUID()

        val payload = buildPayload(
            id = accountId.toString(),
            owner = UUID.randomUUID().toString(),
            createdAt = "abc",
            status = "ENABLED"
        )

        whenever(accountRepository.findById(accountId)).thenReturn(null)

        assertThrows(NumberFormatException::class.java) {
            accountService.createAccountIfNotExists(payload)
        }

        verify(accountRepository).findById(accountId)
        verify(accountRepository, never()).save(org.mockito.kotlin.any())
        verifyNoMoreInteractions(accountRepository)
    }

    @Test
    fun `deve lançar erro quando status for invalido`() {
        val accountId = UUID.randomUUID()

        val payload = buildPayload(
            id = accountId.toString(),
            owner = UUID.randomUUID().toString(),
            createdAt = "1749661080",
            status = "UNKNOWN"
        )

        whenever(accountRepository.findById(accountId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            accountService.createAccountIfNotExists(payload)
        }

        verify(accountRepository).findById(accountId)
        verify(accountRepository, never()).save(org.mockito.kotlin.any())
        verifyNoMoreInteractions(accountRepository)
    }

    @Test
    fun `deve persistir com balance zero mesmo quando payload nao traz balance`() {
        val accountId = UUID.randomUUID()

        val payload = buildPayload(
            id = accountId.toString(),
            owner = UUID.randomUUID().toString(),
            createdAt = "1749661080",
            status = "ENABLED"
        )

        whenever(accountRepository.findById(accountId)).thenReturn(null)

        accountService.createAccountIfNotExists(payload)

        val captor = argumentCaptor<AccountEntity>()
        verify(accountRepository).save(captor.capture())

        assertNotNull(captor.firstValue)
        assertEquals(BigDecimal.ZERO, captor.firstValue.balance)
    }

    private fun buildPayload(
        id: String,
        owner: String,
        createdAt: String,
        status: String
    ): AccountMessagePayload {
        return AccountMessagePayload(
            account = AccountMessage(
                id = id,
                owner = owner,
                createdAt = createdAt,
                status = status
            )
        )
    }
}