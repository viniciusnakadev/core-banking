package br.com.vgn.transaction.idempotency

import br.com.vgn.transaction.dto.TransactionRequestDTO
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object IdempotencyHashUtils {

    fun generate(request: TransactionRequestDTO): String {
        val raw = buildString {
            append(request.accountId)
            append("|")
            append(request.type.name)
            append("|")
            append(request.amount.value.stripTrailingZeros().toPlainString())
            append("|")
            append(request.amount.currency.uppercase())
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }
}