package uk.gov.justice.digital.hmpps.prisonerlocationapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DownloadTest {
  @ParameterizedTest
  @CsvSource(
    "null,null",
    ",",
    "C_NOMIS_OFFENDER_28052025_01.zip,20250528_01.zip",
    "C_NOMIS_OFFENDER_28052025_02.zip,20250528_02.zip",
    "some file.zip,some file.zip",
  )
  fun getIsoDateName(input: String?, expected: String?) {
    assertThat(Download(input, null, null).getIsoDateName()).isEqualTo(expected)
  }
}
