package uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.service.DownloadService
import java.time.Instant

@RestController
@Validated
@RequestMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
class DownloadResource(private val downloadService: DownloadService) {

  @GetMapping("/list")
  @Operation(
    summary = "List all downloads",
    description = "List all available extracts. Requires role PRISONER_DOWNLOAD_UI",
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
  @PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOAD_UI')")
  suspend fun getList(): Downloads = downloadService.getList()

  @GetMapping("/today")
  @Operation(
    summary = "Retrieve today's download extract information",
    description = "Retrieves information on today's download. Requires role PRISONER_DOWNLOAD_UI",
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
  @PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOAD_UI')")
  suspend fun getToday(): Download = downloadService.getToday() ?: throw ExtractFileNotFound()

  @GetMapping("/download/{filename}", produces = ["application/x-zip-compressed"])
  @Operation(
    summary = "Download specified file",
    description = "Download specified file. Requires role PRISONER_DOWNLOAD_UI or PRISONER_DOWNLOAD__RO",
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
  @PreAuthorize("hasAnyRole('ROLE_PRISONER_DOWNLOAD_UI', 'ROLE_PRISONER_DOWNLOAD__RO')")
  suspend fun downloadFile(@PathVariable filename: String): ByteArray = downloadService.download(filename) ?: throw ExtractFileNotFound()

  @DeleteMapping("/delete/{filename}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Hidden
  @PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOAD__RW')")
  // Hidden as not part of standard functionality, but useful if file uploaded accidentally or for testing
  suspend fun deleteFile(@PathVariable filename: String) {
    downloadService.delete(filename)
  }

  @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Hidden
  @PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOAD__RW')")
  // Hidden as not part of standard functionality, but useful if file needs to be uploaded or for testing
  suspend fun uploadFile(@RequestPart("file") file: FilePart) {
    val content = file.content().map { dataBuffer ->
      ByteArray(dataBuffer.readableByteCount()).apply {
        dataBuffer.read(this)
        DataBufferUtils.release(dataBuffer)
      }
    }.awaitFirst()
    downloadService.upload(file.filename(), content)
  }
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
)

class ExtractFileNotFound : RuntimeException("Extract file not found")
