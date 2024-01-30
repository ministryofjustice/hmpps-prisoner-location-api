package uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource.AmendmentResource

class AmendmentResourceExcludedIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var context: ApplicationContext

  @Test
  internal fun `test amendment bean not defined`() {
    assertThatThrownBy {
      context.getBean(AmendmentResource::class.simpleName!!)
    }.isInstanceOf(NoSuchBeanDefinitionException::class.java)
  }

  @Test
  internal fun `test endpoints not defined`() {
    val result = context.getBeansOfType(RequestMappingHandlerMapping::class.java).flatMap { (_, mapping) ->
      mapping.handlerMethods.flatMap { (mappingInfo, _) ->
        mappingInfo.patternsCondition.patterns.map { it.patternString }
      }
    }
    assertThat(result)
      .contains("/today")
      .contains("/list")
      .doesNotContain("/delete/{filename}")
      .doesNotContain("/upload")
  }
}
