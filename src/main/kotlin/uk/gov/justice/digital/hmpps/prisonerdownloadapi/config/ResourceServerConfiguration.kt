package uk.gov.justice.digital.hmpps.prisonerdownloadapi.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager = false)
class ResourceServerConfiguration {
  @Bean
  fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
    http {
      // Can't have CSRF protection as requires session
      csrf { disable() }
      authorizeExchange {
        listOf(
          "/webjars/**", "/favicon.ico", "/csrf",
          "/health/**", "/info", "/h2-console/**",
          "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
        ).forEach { authorize(it, permitAll) }
        authorize(anyExchange, authenticated)
      }
      oauth2ResourceServer { jwt { jwtAuthenticationConverter = AuthAwareTokenConverter() } }
    }

  @Bean
  @Order(1)
  fun legacyFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
    http {
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
  private val defaultClientRegistration = ClientRegistration.withRegistrationId("prisoner-download-api")
    .clientId("default-to-be-set")
    .tokenUri("/oauth/token")
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .build()

  override fun authenticate(authentication: Authentication?): Mono<Authentication> {
    if (authentication == null || authentication.name.isNullOrBlank() || authentication.credentials?.toString()?.isBlank() == true) {
      log.info("Credentials missing for user {}", authentication?.name)
      throw BadCredentialsException("Missing credentials")
    }
    log.info("Found credentials of $authentication")

    val request = ClientRegistration.withClientRegistration(defaultClientRegistration)
      .clientId(authentication.name)
      .clientSecret(authentication.credentials.toString())
      .build().let { OAuth2ClientCredentialsGrantRequest(it) }

    // to get the client roles we have to parse the access token response and grab the authorities from them
    return client.getTokenResponse(request).flatMap { jwtDecoder.decode(it.accessToken.tokenValue) }.map { jwt ->
      UsernamePasswordAuthenticationToken(
        authentication.principal,
        authentication.credentials,
        (jwt.claims["authorities"] as List<*>).map { SimpleGrantedAuthority(it.toString()) },
      )
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
