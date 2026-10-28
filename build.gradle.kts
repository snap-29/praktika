plugins {
    java
    jacoco
    kotlin("jvm") version "2.1.21"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.4"
}

extra["testcontainers.version"] = "1.21.4"

group = "ru.ithub"

version = "1.0.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

kotlin { jvmToolchain(21) }

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation(kotlin("reflect"))
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform { excludeTags("integration", "e2e") }
    finalizedBy(tasks.jacocoTestReport)
}

val integrationTest by
    tasks.registering(Test::class) {
        description = "PostgreSQL REST integration and end-to-end tests; requires Docker"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { includeTags("integration", "e2e") }
        shouldRunAfter(tasks.test)
        extensions.configure<JacocoTaskExtension> {
            destinationFile = layout.buildDirectory.file("jacoco/integrationTest.exec").get().asFile
        }
    }

tasks.check { dependsOn(integrationTest) }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    mustRunAfter(integrationTest)
    executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotless {
    java { googleJavaFormat("1.25.2") }
    kotlin { ktfmt("0.54").kotlinlangStyle() }
    kotlinGradle { ktfmt("0.54").kotlinlangStyle() }
}

tasks.bootJar { archiveFileName.set("app.jar") }

// Isolate javac from the Gradle daemon on Windows.
tasks.withType<JavaCompile>().configureEach { options.isFork = true }
