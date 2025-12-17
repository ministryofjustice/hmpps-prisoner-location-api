package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.hmpps.kotlin.auth.AuthAwareReactiveTokenConverter
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveResourceServerConfiguration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
@EnableWebFluxSecurity
class ResourceServerConfiguration : HmppsReactiveResourceServerConfiguration() {
  @Bean
  override fun hmppsSecurityWebFilterChain(http: ServerHttpSecurity, customizer: ResourceServerConfigurationCustomizer): SecurityWebFilterChain = super.hmppsSecurityWebFilterChain(http, customizer)

  @Bean
  @Order(1)
  fun legacyFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http {
    securityMatcher(PathPatternParserServerWebExchangeMatcher("/legacy/**"))
    authorizeExchange { authorize(anyExchange, authenticated) }
    httpBasic { authenticationManager }
  }

  @Bean
  fun reactiveAuthenticationManager(
    hmppsAuthWebClient: WebClient,
    jwtDecoder: ReactiveJwtDecoder,
  ): ReactiveAuthenticationManager = AuthReactiveAuthenticationManager(hmppsAuthWebClient, jwtDecoder)
}

class AuthReactiveAuthenticationManager(hmppsAuthWebClient: WebClient, private val jwtDecoder: ReactiveJwtDecoder) : ReactiveAuthenticationManager {
  private val client = WebClientReactiveClientCredentialsTokenResponseClient().apply { setWebClient(hmppsAuthWebClient) }
  private val defaultClientRegistration = ClientRegistration.withRegistrationId("prisoner-location-api")
    .clientId("default-to-be-set")
    .tokenUri("/oauth/token")
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .build()

  override fun authenticate(authentication: Authentication): Mono<Authentication> {
    if (authentication.name.isBlank() || authentication.credentials.toString().isBlank()) {
      log.info("Credentials missing for user {}", authentication.name)
      throw BadCredentialsException("Missing credentials")
    }
    log.info("Found credentials of $authentication")

    val request = ClientRegistration.withClientRegistration(defaultClientRegistration)
      .clientId(authentication.name)
      .clientSecret(authentication.credentials.toString())
      .build().let { OAuth2ClientCredentialsGrantRequest(it) }

    // to get the client roles we have to parse the access token response and grab the authorities from them
    return client.getTokenResponse(request).flatMap {
      jwtDecoder.decode(it.accessToken.tokenValue)
    }.flatMap {
      AuthAwareReactiveTokenConverter().convert(it)
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
