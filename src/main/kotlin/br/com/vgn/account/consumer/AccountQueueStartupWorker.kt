package br.com.vgn.account.consumer

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.jboss.logging.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ApplicationScoped
class AccountQueueStartupWorker(
    private val accountQueueConsumer: AccountQueueConsumer
) {

    private val log = Logger.getLogger(AccountQueueStartupWorker::class.java)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun onStart(@Observes event: StartupEvent) {
        log.info("Starting account queue worker")

        executor.submit {
            accountQueueConsumer.consumeInitialLoad()
            accountQueueConsumer.consumeContinuous()
        }
    }

    fun onStop(@Observes event: ShutdownEvent) {
        log.info("Stopping account queue worker")

        accountQueueConsumer.stop()
        executor.shutdown()

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (ex: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}