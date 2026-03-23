package br.com.vgn.transaction.domain

import br.com.vgn.account.domain.AccountEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "financial_transaction")
class FinancialTransactionEntity (

    @Id
    @Column(name = "id", nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    var account: AccountEntity? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: TransactionType? = null,

    @Column(name = "amount_value", nullable = false, precision = 19, scale = 2)
    var amountValue: BigDecimal = BigDecimal.ZERO,

    @Column(name = "amount_currency", nullable = false, length = 3)
    var amountCurrency: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TransactionStatus? = null,

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    var idempotencyKey: String? = null,

    @Column(name = "request_hash", nullable = false, length = 128)
    var requestHash: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime? = null

)