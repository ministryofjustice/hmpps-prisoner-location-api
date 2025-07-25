plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "8.3.4"
  kotlin("plugin.spring") version "2.2.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

// okhttp only used by the AWS SDK kotlin library so okay to pin
ext["okhttp.version"] = "5.0.0-alpha.14"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.4.11")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.4.10")

  implementation("aws.sdk.kotlin:s3:1.5.3")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
  // Leaving at 1.43.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L14
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.50.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.9")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.4.11")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.31") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.34")
  testImplementation("org.wiremock:wiremock-standalone:3.13.1")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.addAll("-Xjvm-default=all", "-Xwhen-guards")
  }
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
