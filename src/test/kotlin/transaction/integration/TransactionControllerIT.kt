package transaction.integration
import jakarta.enterprise.context.ApplicationScoped

import br.com.vgn.account.domain.AccountEntity
import br.com.vgn.account.domain.AccountStatus
import br.com.vgn.account.repository.AccountRepository
import br.com.vgn.common.http.HttpHeaders
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@QuarkusTest
class TransactionControllerIT {

    @Inject
    lateinit var helper: TransactionControllerITHelper

    private lateinit var existingAccountId: UUID

    @BeforeEach
    fun setup() {
        existingAccountId = UUID.randomUUID()
        helper.createAccount(existingAccountId, BigDecimal("100.00"))
    }

    @Test
    fun `deve processar CREDIT com sucesso e atualizar saldo`() {
        val body = """
            {
              "accountId": "$existingAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 50.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(200)
            .body("transaction.type", equalTo("CREDIT"))
            .body("transaction.status", equalTo("SUCCEEDED"))
            .body("transaction.amount.value", equalTo(50.0f))
            .body("transaction.amount.currency", equalTo("BRL"))
            .body("account.id", equalTo(existingAccountId.toString()))
            .body("account.balance.amount", equalTo(150.0f))
            .body("account.balance.currency", equalTo("BRL"))

        assertBalance(existingAccountId, BigDecimal("150.00"))
    }

    @Test
    fun `deve processar DEBIT com sucesso e atualizar saldo`() {
        val body = """
            {
              "accountId": "$existingAccountId",
              "type": "DEBIT",
              "amount": {
                "value": 30.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(200)
            .body("transaction.type", equalTo("DEBIT"))
            .body("transaction.status", equalTo("SUCCEEDED"))
            .body("transaction.amount.value", equalTo(30.0f))
            .body("transaction.amount.currency", equalTo("BRL"))
            .body("account.id", equalTo(existingAccountId.toString()))
            .body("account.balance.amount", equalTo(70.0f))
            .body("account.balance.currency", equalTo("BRL"))

        assertBalance(existingAccountId, BigDecimal("70.00"))
    }

    @Test
    fun `deve retornar FAILED quando DEBIT nao tiver saldo suficiente e manter saldo`() {
        val body = """
            {
              "accountId": "$existingAccountId",
              "type": "DEBIT",
              "amount": {
                "value": 9999.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(200)
            .body("transaction.type", equalTo("DEBIT"))
            .body("transaction.status", equalTo("FAILED"))
            .body("transaction.amount.value", equalTo(9999.0f))
            .body("transaction.amount.currency", equalTo("BRL"))
            .body("account.id", equalTo(existingAccountId.toString()))
            .body("account.balance.amount", equalTo(100.0f))
            .body("account.balance.currency", equalTo("BRL"))

        assertBalance(existingAccountId, BigDecimal("100.00"))
    }

    @Test
    fun `deve retornar 404 quando conta nao existir`() {
        val nonexistentAccountId = UUID.randomUUID()

        val body = """
            {
              "accountId": "$nonexistentAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 10.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(404)
            .body("code", equalTo("ACCOUNT_NOT_FOUND"))
    }

    @Test
    fun `deve retornar replay quando Idempotency-Key for repetida com mesmo payload e manter saldo correto`() {
        val idempotencyKey = UUID.randomUUID().toString()

        val body = """
            {
              "accountId": "$existingAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 25.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        val firstResponse =
            given()
                .contentType("application/json")
                .header(HttpHeaders.IDEMPOTENCY_KEY, idempotencyKey)
                .body(body)
            .`when`()
                .post("/transactions")
            .then()
                .statusCode(200)
                .extract()

        val firstTransactionId = firstResponse.path<String>("transaction.id")

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, idempotencyKey)
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(200)
            .body("transaction.id", equalTo(firstTransactionId))
            .body("account.balance.amount", equalTo(125.0f))

        assertBalance(existingAccountId, BigDecimal("125.00"))
    }

    @Test
    fun `deve retornar 409 quando Idempotency-Key for repetida com payload diferente`() {
        val idempotencyKey = UUID.randomUUID().toString()

        val firstBody = """
            {
              "accountId": "$existingAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 10.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        val secondBody = """
            {
              "accountId": "$existingAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 99.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, idempotencyKey)
            .body(firstBody)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(200)

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, idempotencyKey)
            .body(secondBody)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(409)
            .body("code", equalTo("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `deve retornar 400 quando Idempotency-Key nao for enviado`() {
        val body = """
            {
              "accountId": "$existingAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 10.00,
                "currency": "BRL"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(400)
            .body("code", equalTo("BAD_REQUEST"))
    }

    @Test
    fun `deve retornar 400 quando currency for invalida`() {
        val body = """
            {
              "accountId": "$existingAccountId",
              "type": "CREDIT",
              "amount": {
                "value": 10.00,
                "currency": "ABC"
              }
            }
        """.trimIndent()

        given()
            .contentType("application/json")
            .header(HttpHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
            .body(body)
        .`when`()
            .post("/transactions")
        .then()
            .statusCode(400)
            .body("code", equalTo("INVALID_CURRENCY"))
    }

    fun assertBalance(accountId: UUID, expectedBalance: BigDecimal) {
        val balance = helper.findBalance(accountId)
        assertNotNull(balance)
        assertEquals(expectedBalance, balance)
    }
}

@ApplicationScoped
class TransactionControllerITHelper(
    private val accountRepository: AccountRepository
) {

    @Transactional
    fun createAccount(accountId: UUID, balance: BigDecimal) {
        val existing = accountRepository.findById(accountId)
        if (existing == null) {
            accountRepository.save(
                AccountEntity(
                    id = accountId,
                    ownerId = UUID.randomUUID(),
                    balance = balance,
                    status = AccountStatus.ENABLED,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now()
                )
            )
        }
    }

    @Transactional
    fun findBalance(accountId: UUID): BigDecimal? {
        return accountRepository.findById(accountId)?.balance
    }
}