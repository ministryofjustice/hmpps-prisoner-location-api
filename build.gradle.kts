plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "7.1.1"
  kotlin("plugin.spring") version "2.1.10"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

// okhttp only used by the AWS SDK kotlin library so okay to pin
ext["okhttp.version"] = "5.0.0-alpha.14"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.2.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.3.0")

  implementation("aws.sdk.kotlin:s3:1.4.16")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.1")
  // Leaving at 1.43.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L14
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.46.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.4")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.2.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.28")
  testImplementation("org.wiremock:wiremock-standalone:3.11.0")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
