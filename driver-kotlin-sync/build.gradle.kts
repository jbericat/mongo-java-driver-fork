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
    `java-library`

    // Test based plugins
    id("com.diffplug.spotless")
    id("org.jetbrains.dokka")
    id("io.gitlab.arturbosch.detekt")
}

repositories {
    mavenCentral()
    google()
}

base.archivesName.set("mongodb-driver-kotlin-sync")

description = "The MongoDB Kotlin Driver"

ext.set("pomName", "MongoDB Kotlin Driver")

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("$projectDir/src/integration/kotlin")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
        compileClasspath += project(":driver-sync").sourceSets.test.get().output
        runtimeClasspath += project(":driver-sync").sourceSets.test.get().output
        compileClasspath += project(":driver-core").sourceSets.test.get().output
        runtimeClasspath += project(":driver-core").sourceSets.test.get().output
        compileClasspath += project(":bson").sourceSets.test.get().output
        runtimeClasspath += project(":bson").sourceSets.test.get().output
    }
}

val integrationTestImplementation: Configuration by
    configurations.getting { extendsFrom(configurations.testImplementation.get()) }

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    api(project(path = ":bson", configuration = "default"))
    api(project(path = ":driver-sync", configuration = "default"))
    implementation(project(path = ":bson-kotlin", configuration = "default"))

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.github.classgraph:classgraph:4.8.154")

    integrationTestImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    integrationTestImplementation(project(path = ":driver-sync"))
    integrationTestImplementation(project(path = ":driver-core"))
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

spotbugs {
    showProgress.set(true)

    tasks.getByName("spotbugsIntegrationTest") { enabled = false }
}

// ===========================
//     Test Configuration
// ===========================
val integrationTest =
    tasks.create("integrationTest", Test::class) {
        description = "Runs the integration tests."
        group = "verification"

        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
    }

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

afterEvaluate { tasks.jar { manifest { attributes["Automatic-Module-Name"] = "org.mongodb.driver.kotlin.sync" } } }
