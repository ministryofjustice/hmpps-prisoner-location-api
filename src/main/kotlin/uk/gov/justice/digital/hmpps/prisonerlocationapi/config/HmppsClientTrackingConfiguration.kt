package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import com.nimbusds.jwt.SignedJWT
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.clienttracking.HmppsClientTrackingWebFilter
import java.text.ParseException

@Configuration
class HmppsClientTrackingConfiguration {
  @Bean
  fun hmppsClientTrackingWebFilter() = HmppsClientTrackingWebFilter { token ->
    if (token.startsWith("Bearer", ignoreCase = true) == true) {
      try {
        val jwtBody = SignedJWT.parse(token.replace("Bearer ", "")).jwtClaimsSet
        val currentSpan = Span.current()
        val user = jwtBody.getClaim("user_name")
        user?.run { currentSpan.setAttribute("username", user.toString()) }
        currentSpan.setAttribute("clientId", jwtBody.getClaim("client_id").toString())
      } catch (e: ParseException) {
        log.warn("problem decoding jwt public key for application insights", e)
      }
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
