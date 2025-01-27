package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-location-api-dev.prison.service.justice.gov.uk").description("Development"),
        Server().url("https://prisoner-location-api-preprod.prison.service.justice.gov.uk").description("Pre-Production"),
        Server().url("https://prisoner-location-api.prison.service.justice.gov.uk").description("Production"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .info(
      Info().title("NOMIS Prisoner Location API")
        .version(version)
        .description("Provides the NOMIS location extract as a download")
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .tags(
      listOf(
        Tag().name("Popular")
          .description("The most popular endpoints. Look here first when deciding which endpoint to use."),
        Tag().name("Deprecated")
          .description("Endpoints that should no longer be used and will be removed in a future release"),
        Tag().name("User interface")
          .description("Endpoints that are designed for the front end and shouldn't be used without consultation"),
      ),
    )
    .components(
      Components().addSecuritySchemes(
        "prisoner-location-ui-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_PRISONER_LOCATION_UI"),
      ).addSecuritySchemes(
        "view-prisoner-location-data-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_PRISONER_LOCATION__RO"),
      ),
    )
    .addSecurityItem(
      SecurityRequirement()
        .addList("prisoner-location-ui-role", listOf("read"))
        .addList("view-prisoner-location-data-role", listOf("read")),
    )

  @Bean
  fun openAPICustomiser(): OpenApiCustomizer = OpenApiCustomizer {
    it.components.schemas.forEach { (_, schema: Schema<*>) ->
      val properties = schema.properties ?: mutableMapOf()
      for (propertyName in properties.keys) {
        val propertySchema = properties[propertyName]!!
        if (propertySchema.format == "date-time") {
          properties.replace(
            propertyName,
            StringSchema()
              .example("2021-07-05T10:35:17")
              .pattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")
              .description(propertySchema.description)
              .required(propertySchema.required),
          )
        }
      }
    }
  }
}

private fun SecurityScheme.addBearerJwtRequirement(role: String): SecurityScheme = type(SecurityScheme.Type.HTTP)
  .scheme("bearer")
  .bearerFormat("JWT")
  .`in`(SecurityScheme.In.HEADER)
  .name("Authorization")
  .description("A HMPPS Auth access token with the `$role` role.")
