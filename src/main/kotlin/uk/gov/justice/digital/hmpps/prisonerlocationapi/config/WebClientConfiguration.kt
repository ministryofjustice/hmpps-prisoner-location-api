package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.hmpps-auth}") private val hmppsAuthEndpoint: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:10s}") val timeout: Duration,
) {

  @Bean
  fun hmppsAuthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(hmppsAuthEndpoint, timeout)

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(hmppsAuthEndpoint, healthTimeout)
}
