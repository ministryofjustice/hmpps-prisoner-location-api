package uk.gov.justice.digital.hmpps.hmppsprisonerdownloadapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class HmppsPrisonerDownloadApi

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerDownloadApi>(*args)
}
