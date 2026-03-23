package br.com.vgn.account.consumer

import software.amazon.awssdk.services.sqs.model.Message

data class ProcessResult(
    val message: Message,
    val success: Boolean
)