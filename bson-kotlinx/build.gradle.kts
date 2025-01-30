/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization")
    id("java-library")

    // Test based plugins
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
}

repositories {
    mavenCentral()
    google()
}

base.archivesName.set("bson-kotlinx")

description = "Bson Kotlinx Codecs"

ext.set("pomName", "Bson Kotlinx")

java {
    registerFeature("dateTimeSupport") { usingSourceSet(sourceSets["main"]) }
    registerFeature("jsonSupport") { usingSourceSet(sourceSets["main"]) }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib.jdk8)

    implementation(platform(libs.kotlinx.serialization))
    implementation(libs.kotlinx.serialization.core)
    "dateTimeSupportImplementation"(libs.kotlinx.serialization.datetime)
    "jsonSupportImplementation"(libs.kotlinx.serialization.json)

    api(project(path = ":bson", configuration = "default"))
    implementation(libs.kotlin.reflect)

    testImplementation(project(path = ":driver-core", configuration = "default"))
    testImplementation(libs.junit.kotlin)
    testImplementation(libs.kotlinx.serialization.datetime)
    testImplementation(libs.kotlinx.serialization.json)
}

kotlin { explicitApi() }

tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "1.8" }

// ===========================
//     Code Quality checks
// ===========================
spotless {
    kotlinGradle {
        ktfmt("0.39").dropboxStyle().configure { it.setMaxWidth(120) }
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
        licenseHeaderFile(rootProject.file("config/mongodb.license"), "(group|plugins|import|buildscript|rootProject)")
    }

    kotlin {
        target("**/*.kt")
        ktfmt().dropboxStyle().configure { it.setMaxWidth(120) }
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
        licenseHeaderFile(rootProject.file("config/mongodb.license"))
    }

    format("extraneous") {
        target("*.xml", "*.yml", "*.md")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}

tasks.named("check") { dependsOn("spotlessApply") }

detekt {
    allRules = true // fail build on any finding
    buildUponDefaultConfig = true // preconfigure defaults
    config = rootProject.files("config/detekt/detekt.yml") // point to your custom config defining rules to run,
    // overwriting default behavior
    baseline = rootProject.file("config/detekt/baseline.xml") // a way of suppressing issues before introducing detekt
    source =
        files(
            file("src/main/kotlin"),
            file("src/test/kotlin"),
            file("src/integrationTest/kotlin"),
        )
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true) // observe findings in your browser with structure and code snippets
        xml.required.set(true) // checkstyle like format mainly for integrations like Jenkins
        txt.required.set(false) // similar to the console output, contains issue signature to manually edit
    }
}

spotbugs { showProgress.set(true) }

// ===========================
//     Test Configuration
// ===========================

tasks.test { useJUnitPlatform() }

// ===========================
//     Dokka Configuration
// ===========================
val dokkaOutputDir = "${rootProject.buildDir}/docs/${base.archivesName.get()}"

tasks.dokkaHtml.configure {
    outputDirectory.set(file(dokkaOutputDir))
    moduleName.set(base.archivesName.get())
}

val cleanDokka by tasks.register<Delete>("cleanDokka") { delete(dokkaOutputDir) }

project.parent?.tasks?.named("docs") {
    dependsOn(tasks.dokkaHtml)
    mustRunAfter(cleanDokka)
}

tasks.javadocJar.configure {
    dependsOn(cleanDokka, tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaOutputDir)
}

// ===========================
//     Sources publishing configuration
// ===========================
tasks.sourcesJar { from(project.sourceSets.main.map { it.kotlin }) }

afterEvaluate { tasks.jar { manifest { attributes["Automatic-Module-Name"] = "org.mongodb.bson.kotlinx" } } }
