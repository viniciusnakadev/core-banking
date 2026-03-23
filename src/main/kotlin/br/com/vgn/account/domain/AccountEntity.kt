package br.com.vgn.account.domain

import br.com.vgn.account.domain.AccountStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Entity
@Table(name = "account")
class AccountEntity (

    @Id
    @Column(name = "id", nullable = false)
    var id: UUID? = null,

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID? = null,

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: AccountStatus? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime? = null)
{

    @PreUpdate
    fun preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
    }

    companion object {
        fun fromMessage(
            id: UUID,
            ownerId: UUID,
            createdAtEpochSeconds: Long,
            status: AccountStatus
        ): AccountEntity {
            val createdAt = Instant.ofEpochSecond(createdAtEpochSeconds).atOffset(ZoneOffset.UTC)
            return AccountEntity(
                id = id,
                ownerId = ownerId,
                balance = BigDecimal.ZERO,
                status = status,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }

}