package uk.gov.justice.digital.hmpps.prisonerdownloadapi.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
  fun reactiveAuthenticationManager(hmppsAuthWebClient: WebClient): ReactiveAuthenticationManager = AuthReactiveAuthenticationManager(hmppsAuthWebClient)
}

@OptIn(ExperimentalEncodingApi::class)
class AuthReactiveAuthenticationManager(private val hmppsAuthWebClient: WebClient) : ReactiveAuthenticationManager {
  override fun authenticate(authentication: Authentication?): Mono<Authentication> {
    if (authentication == null || authentication.name.isNullOrBlank() || authentication.credentials?.toString()?.isBlank() == true) {
      log.info("Credentials missing for user {}", authentication?.name)
      throw BadCredentialsException("Missing credentials")
    }
    log.info("Found credentials of $authentication")
    val credentials = Base64.encode("${authentication.principal}:${authentication.credentials}".encodeToByteArray())

    // TODO (PGP): Switch to spring security code to process token response
    return hmppsAuthWebClient.post().uri("/oauth/token?grant_type=client_credentials")
      .header("Authorization", "Basic $credentials")
      .exchangeToMono { response ->
        if (response.statusCode() == HttpStatus.OK) {
          Mono.just(
            UsernamePasswordAuthenticationToken(
              authentication.principal,
              authentication.credentials,
              listOf(SimpleGrantedAuthority("ROLE_PRISONER_DOWNLOADS")),
            ),
          )
        } else {
          throw BadCredentialsException("That didn't work - ${response.statusCode()}")
        }
      }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
