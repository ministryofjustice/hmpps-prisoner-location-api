package uk.gov.justice.digital.hmpps.prisonerlocationapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HmppsPrisonerLocationApi

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerLocationApi>(*args)
}
