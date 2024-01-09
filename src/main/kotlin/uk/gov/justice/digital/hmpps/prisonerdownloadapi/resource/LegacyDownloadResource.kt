package uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.service.DownloadService

@RestController
@Validated
@RequestMapping("/legacy", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOADS')")
class LegacyDownloadResource(private val downloadService: DownloadService) {

  @GetMapping("/download/{filename}", produces = ["application/x-zip-compressed"])
  @Operation(
    summary = "Retrieve today's download extract information using basic authentication",
    description = """Retrieves information on today's download using basic authentication.
       This is a legacy endpoint provided for backwards compatibility.  It is expected that 
       clients will transition onto the /download/{filename} endpoint using bearer (oauth2)
       authentication instead.
       Requires role PRISONER_DOWNLOADS""",
    deprecated = true,
    responses = [
      ApiResponse(responseCode = "200", description = "Information on today's file"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No file for today exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun downloadFile(@PathVariable filename: String): ByteArray = downloadService.download(filename) ?: throw ExtractFileNotFound()
}