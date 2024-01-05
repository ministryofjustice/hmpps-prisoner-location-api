package uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.ErrorResponse
import java.time.LocalDateTime

@RestController
@Validated
@RequestMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOADS')")
class DownloadResource {

  @GetMapping("/list")
  @Operation(
    summary = "List all downloads",
    description = "List all available extracts. Requires role PRISONER_DOWNLOADS",
    responses = [
      ApiResponse(responseCode = "200", description = "List of extracts returned"),
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
    ],
  )
  suspend fun getList(): Downloads = Downloads(listOf(Download("file", 10, LocalDateTime.now())))

  @GetMapping("/today")
  @Operation(
    summary = "Retrieve today's download extract information",
    description = "Retrieves information on today's download. Requires role PRISONER_DOWNLOADS",
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
  suspend fun getToday(): Download = Download("file", 10, LocalDateTime.now())
}

@Schema(description = "NOMIS Extract downloads")
data class Downloads(
  @Schema(description = "List of possible downloads")
  val files: List<Download>,
)

@Schema(description = "NOMIS Extract download")
data class Download(
  @Schema(description = "File name")
  val name: String,

  @Schema(description = "File size")
  val size: Int,

  @Schema(description = "Date time the file was last modified")
  val lastModified: LocalDateTime,
)
