package uk.gov.justice.digital.hmpps.prisonerlocationapi.resource

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerlocationapi.service.DownloadService
import java.time.Instant

@RestController
@Validated
@RequestMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
class DownloadResource(private val downloadService: DownloadService) {

  @GetMapping("/list")
  @Tag(name = "User interface")
  @Operation(
    summary = "List all downloads",
    description = "List all available extracts. Requires role PRISONER_LOCATION_UI",
    security = [SecurityRequirement(name = "prisoner-location-ui-role")],
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
  @PreAuthorize("hasRole('ROLE_PRISONER_LOCATION_UI')")
  suspend fun getList(): Downloads = downloadService.getList()

  @GetMapping("/today")
  @Tag(name = "User interface")
  @Operation(
    summary = "Retrieve today's download extract information",
    description = "Retrieves information on today's download. Requires role PRISONER_LOCATION_UI",
    security = [SecurityRequirement(name = "prisoner-location-ui-role")],
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
  @PreAuthorize("hasRole('ROLE_PRISONER_LOCATION_UI')")
  suspend fun getToday(): Download = downloadService.getToday() ?: throw ExtractFileNotFound()

  @GetMapping("/download/{filename}", produces = ["application/x-zip-compressed"])
  @Tag(name = "Popular")
  @Operation(
    summary = "Download specified file",
    description = """Download specified file. The filename is normally of form 'YYYYMMDD.zip' where YYYY is a four digit
      year, MM is a two digit month and DD is a two digit day e.g. 20240904.zip would be to request the download
      for 4th September 2024. A 404 response code (not found) will be returned if the file hasn't been created yet or
      is not available to download.
      This endpoint requires role PRISONER_LOCATION_UI or PRISONER_LOCATION__RO""",
    security = [SecurityRequirement(name = "view-prisoner-location-data-role"), SecurityRequirement(name = "prisoner-location-ui-role")],
    responses = [
      ApiResponse(responseCode = "200", description = "File download"),
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
        description = "No file exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PreAuthorize("hasAnyRole('ROLE_PRISONER_LOCATION_UI', 'ROLE_PRISONER_LOCATION__RO')")
  suspend fun downloadFile(@PathVariable filename: String): ByteArray = downloadService.download(filename) ?: throw ExtractFileNotFound()
}

@Schema(description = "NOMIS Extract downloads")
data class Downloads(
  @Schema(description = "List of possible downloads")
  val files: List<Download>,
)

@Schema(description = "NOMIS Extract download")
data class Download(
  @Schema(description = "File name")
  val name: String?,

  @Schema(description = "File size")
  val size: Long?,

  @Schema(description = "Date time the file was last modified")
  val lastModified: Instant?,
) {
  private companion object {
    private val dateRegex = ".*(?<day>\\d{2})(?<month>\\d{2})(?<year>\\d{4})".toRegex()
    private val replacement = "\${year}\${month}\${day}"
  }

  @JsonIgnore
  fun getIsoDateName(): String? {
    // names will be of format C_NOMIS_OFFENDER_DDMMYYY_On.zip.
    // This function then formats to C_NOMIS_OFFENDER_YYYYMMDD_On.zip.
    // So C_NOMIS_OFFENDER_28052025_01.zip will become C_NOMIS_OFFENDER_20250528_01.zip
    // whick will then allow us to sort descending to get the latest file
    return name?.let { name.replace(dateRegex, replacement) }
  }
}

class ExtractFileNotFound : RuntimeException("Extract file not found")
