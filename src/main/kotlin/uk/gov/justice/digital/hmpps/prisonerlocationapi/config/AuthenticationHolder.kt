package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.kotlin.auth.AuthAwareAuthenticationToken

@Component
class AuthenticationHolder {

  suspend fun getAuthentication(): Authentication =
    ReactiveSecurityContextHolder.getContext().awaitSingle().authentication

  suspend fun currentPrincipal(): String =
    getAuthentication().let {
      when (it) {
        is AuthAwareAuthenticationToken -> it.principal
        else -> "anonymous"
      }
    }
}
