package br.com.vgn.account.consumer

import br.com.vgn.account.service.AccountService
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@ApplicationScoped
class AccountQueueConsumer(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val accountService: AccountService,
    private val tracer: Tracer
) {

    @ConfigProperty(name = "app.sqs.queue-url")
    lateinit var queueUrl: String

    private val log = Logger.getLogger(AccountQueueConsumer::class.java)
    private val running = AtomicBoolean(true)

    fun stop() {
        running.set(false)
    }

    fun consumeInitialLoad() {
        var totalProcessed = 0
        var emptyPolls = 0
        val maxEmptyPolls = 3

        val start = System.nanoTime()

        log.info("Starting initial SQS load consumption")

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            while (running.get() && emptyPolls < maxEmptyPolls) {
                val response = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .build()
                )

                val messages = response.messages()

                if (messages.isEmpty()) {
                    emptyPolls++
                    log.infof(
                        "Initial load poll returned empty. emptyPolls=%d/%d",
                        emptyPolls,
                        maxEmptyPolls
                    )
                    continue
                }

                emptyPolls = 0

                val successMessages = processBatchWithVirtualThreads(executor, messages)
                deleteBatch(successMessages)

                totalProcessed += successMessages.size

                if (totalProcessed % 1000 == 0) {
                    log.infof("Initial load progress. totalProcessed=%d", totalProcessed)
                }
            }
        }

        val end = System.nanoTime()
        val durationNanos = end - start
        val durationMs = durationNanos / 1_000_000.0
        val durationSeconds = durationNanos / 1_000_000_000.0
        val throughput = if (durationSeconds > 0) totalProcessed / durationSeconds else 0.0

        log.infof(
            "Initial load finished. totalProcessed=%d durationMs=%.2f durationSeconds=%.2f throughput=%.2f msg/s",
            totalProcessed,
            durationMs,
            durationSeconds,
            throughput
        )
    }

    fun consumeContinuous() {
        log.info("Starting continuous SQS consumption")

        while (running.get()) {
            try {
                val response = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)
                        .build()
                )

                val messages = response.messages()
                if (messages.isEmpty()) {
                    continue
                }

                val start = System.nanoTime()

                val successMessages = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                    processBatchWithVirtualThreads(executor, messages)
                }

                deleteBatch(successMessages)

                val end = System.nanoTime()
                val durationNanos = end - start
                val durationMs = durationNanos / 1_000_000.0
                val durationSeconds = durationNanos / 1_000_000_000.0
                val throughput = if (durationSeconds > 0) successMessages.size / durationSeconds else 0.0

                log.infof(
                    "Continuous batch processed. received=%d success=%d durationMs=%.2f throughput=%.2f msg/s",
                    messages.size,
                    successMessages.size,
                    durationMs,
                    throughput
                )
            } catch (ex: Exception) {
                log.error("Error in continuous SQS consumption loop", ex)
            }
        }

        log.info("Continuous SQS consumer stopped")
    }

    private fun processBatchWithVirtualThreads(
        executor: java.util.concurrent.ExecutorService,
        messages: List<Message>
    ): List<Message> {
        val futures = messages.map { sqsMessage ->
            executor.submit<ProcessResult> {

                val span = tracer.spanBuilder("sqs.account.process").startSpan()
                val correlationId = UUID.randomUUID().toString()
                MDC.put("correlationId", correlationId)

                try {
                    span.setAttribute("correlation.id", correlationId)
                    span.setAttribute("sqs.message.id", sqsMessage.messageId())

                    val payload = objectMapper.readValue(
                        sqsMessage.body(),
                        AccountMessagePayload::class.java
                    )

                    span.setAttribute("account.id", payload.account.id)
                    span.setAttribute("account.owner", payload.account.owner)

                    accountService.createAccountIfNotExists(payload)

                    span.setStatus(StatusCode.OK)
                    ProcessResult(sqsMessage, true)
                } catch (ex: Exception) {
                    span.recordException(ex)
                    span.setStatus(StatusCode.ERROR)
                    log.errorf(ex, "Error processing messageId=%s", sqsMessage.messageId())
                    ProcessResult(sqsMessage, false)
                } finally {
                    span.end()
                    MDC.remove("correlationId")
                }
            }
        }

        return futures.map { it.get() }
            .filter { it.success }
            .map { it.message }
    }

    private fun deleteBatch(successMessages: List<Message>) {
        if (successMessages.isEmpty()) {
            return
        }

        val deleteEntries = successMessages.map {
            DeleteMessageBatchRequestEntry.builder()
                .id(it.messageId())
                .receiptHandle(it.receiptHandle())
                .build()
        }

        sqsClient.deleteMessageBatch(
            DeleteMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(deleteEntries)
                .build()
        )
    }
}