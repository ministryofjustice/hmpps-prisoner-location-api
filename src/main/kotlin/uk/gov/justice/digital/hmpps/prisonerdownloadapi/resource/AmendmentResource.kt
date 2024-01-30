package uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource

import io.swagger.v3.oas.annotations.Hidden
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.service.DownloadService

@RestController
@Validated
@RequestMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
@ConditionalOnProperty(name = ["amendment.enabled"], havingValue = "true")
@PreAuthorize("hasRole('ROLE_PRISONER_DOWNLOAD__RW')")
class AmendmentResource(private val downloadService: DownloadService) {
  @DeleteMapping("/delete/{filename}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Hidden
  // Hidden as not part of standard functionality, but useful if file uploaded accidentally or for testing
  suspend fun deleteFile(@PathVariable filename: String) {
    downloadService.delete(filename)
  }

  @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Hidden
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
