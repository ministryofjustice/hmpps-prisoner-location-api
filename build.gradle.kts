plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.0.0-beta"
  kotlin("plugin.spring") version "2.3.0"
}

configurations {
  implementation {
    exclude(module = "spring-boot-starter-web")
    exclude(module = "spring-boot-starter-tomcat")
  }
  testImplementation { exclude(group = "org.junit.vintage") }
}

// okhttp only used by the AWS SDK kotlin library so okay to pin
ext["okhttp.version"] = "5.1.0"
ext["kotlin-coroutines.version"] = "1.10.2"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.0.0-beta-2")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.security:spring-security-access")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:6.0.0-beta")

  implementation("aws.sdk.kotlin:s3:1.5.104")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
  // Should match the version used in App Insights pulled in by the plugin - https://github.com/ministryofjustice/hmpps-gradle-spring-boot/blob/main/src/main/kotlin/uk/gov/justice/digital/hmpps/gradle/configmanagers/AppInsightsConfigManager.kt#L10
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.53.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.0")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.0.0-beta-2")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.36") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.41")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjvm-default=all", "-Xwhen-guards", "-Xannotation-default-target=param-property")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24
  }
}
