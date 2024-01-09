package uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class OAuthMockServer : WireMockServer(8090) {
  fun stubHealthPing(status: Int) {
    stubFor(
      WireMock.get("/auth/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGrantToken(jwt: String, status: Int = 200) {
    stubFor(
      post(urlPathEqualTo("/auth/oauth/token"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """
              {
                 "access_token": $jwt, 
                 "token_type": "bearer"
              }
              """.trimIndent(),
            )
            .withStatus(status),
        ),
    )
  }
}

class HmppsAuthApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsAuth = OAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsAuth.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsAuth.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsAuth.stop()
  }
}
