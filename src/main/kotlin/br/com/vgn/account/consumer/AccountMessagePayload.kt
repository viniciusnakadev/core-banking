package br.com.vgn.account.consumer

import com.fasterxml.jackson.annotation.JsonProperty

data class AccountMessagePayload(
    val account: AccountMessage
)

data class AccountMessage(
    val id: String,
    val owner: String,
    @JsonProperty("created_at")
    val createdAt: String,
    val status: String
)