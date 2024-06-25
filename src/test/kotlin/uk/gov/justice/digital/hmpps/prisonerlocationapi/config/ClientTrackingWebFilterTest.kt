package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@Import(JwtAuthorisationHelper::class, ClientTrackingWebFilter::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingWebFilterTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingWebFilter: ClientTrackingWebFilter

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthorisationHelper

  private val webFilterChain: WebFilterChain = WebFilterChain { Mono.empty() }
  private val tracer: Tracer = otelTesting.openTelemetry.getTracer("test")

  @Test
  fun shouldAddClientIdAndUserNameToInsightTelemetry() {
    val token = jwtAuthHelper.createJwtAccessToken(username = "bob", clientId = "prisoner-location-client")

    val exchange = MockServerWebExchange.builder(
      MockServerHttpRequest.get("http://prisoner-location")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token").build(),
    ).build()
    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingWebFilter.filter(exchange, webFilterChain) }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("username"), "bob")
        it.hasAttribute(AttributeKey.stringKey("clientId"), "prisoner-location-client")
      })
    })
  }

  @Test
  fun shouldAddOnlyClientIdIfUsernameNullToInsightTelemetry() {
    val token = jwtAuthHelper.createJwtAccessToken(clientId = "prisoner-location-client")
    val exchange = MockServerWebExchange.builder(
      MockServerHttpRequest.get("http://prisoner-location")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token").build(),
    ).build()
    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingWebFilter.filter(exchange, webFilterChain) }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("clientId"), "prisoner-location-client")
        it.hasTotalAttributeCount(1)
      })
    })
  }

  private companion object {
    @Suppress("JUnitMalformedDeclaration")
    @JvmStatic
    @RegisterExtension
    val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
  }
}
