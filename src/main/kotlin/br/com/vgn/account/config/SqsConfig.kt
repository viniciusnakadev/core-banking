package br.com.vgn.account.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.http.apache.ApacheHttpClient
import java.net.URI
import java.time.Duration

@ApplicationScoped
class SqsConfig {

    @ConfigProperty(name = "aws.region")
    lateinit var region: String

    @ConfigProperty(name = "aws.access-key")
    lateinit var accessKey: String

    @ConfigProperty(name = "aws.secret-key")
    lateinit var secretKey: String

    @ConfigProperty(name = "aws.endpoint")
    lateinit var endpoint: String

    @Produces
    @ApplicationScoped
    fun sqsClient(): SqsClient {
        return SqsClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
            .httpClientBuilder(
                ApacheHttpClient.builder()
                    .connectionTimeout(Duration.ofSeconds(5))
                    .socketTimeout(Duration.ofSeconds(30))
            )
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallAttemptTimeout(Duration.ofSeconds(35))
                    .apiCallTimeout(Duration.ofSeconds(60))
                    .build()
            )
            .build()
    }
}