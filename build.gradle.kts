plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.5"
  kotlin("plugin.spring") version "2.4.10"
}

configurations {
  implementation {
    exclude(module = "spring-boot-starter-web")
    exclude(module = "spring-boot-starter-tomcat")
  }
}

// okhttp only used by the AWS SDK kotlin library so okay to pin
ext["okhttp.version"] = "5.1.0"
ext["kotlin-coroutines.version"] = "1.11.0"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.0.0")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.security:spring-security-access")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.0")

  implementation("aws.sdk.kotlin:s3:1.8.9")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  // Should match the version used in App Insights pulled in by the plugin - https://github.com/ministryofjustice/hmpps-gradle-spring-boot/blob/main/src/main/kotlin/uk/gov/justice/digital/hmpps/gradle/configmanagers/AppInsightsConfigManager.kt#L10
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.62.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.3")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.45") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    freeCompilerArgs.add("-Xcollection-literals")
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
  }
}
