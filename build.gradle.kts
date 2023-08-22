import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import com.github.jk1.license.render.JsonReportRenderer
import org.jetbrains.changelog.Changelog

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)
fun systemProperties(key: String) = providers.systemProperty(key)

plugins {
    id("java")
    id("application")
    id("checkstyle")
    id("org.openjfx.javafxplugin") version "0.0.14"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.panteleyev.jpackageplugin") version "1.5.2"
    id("com.github.jk1.dependency-license-report") version "2.5"
    id("org.jetbrains.changelog") version "2.1.2"
    id("net.researchgate.release") version "3.0.2"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0") // tika-core slf4j2 logger
    implementation("org.apache.commons:commons-lang3:3.13.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("commons-io:commons-io:2.13.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.json:json:20230618")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-adapters:1.15.0")
    implementation("io.github.danygold:fx-moshi:1.0.0")
    implementation("org.ahocorasick:ahocorasick:0.6.3")
    implementation("org.controlsfx:controlsfx:11.1.2")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")
    implementation("org.apache.tika:tika-core:2.8.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")

    implementation("org.jetbrains:annotations:24.0.1")
    implementation("com.google.errorprone:error_prone_annotations:2.21.1")

    checkstyle("com.puppycrawl.tools:checkstyle:10.12.2")

    testImplementation("org.testfx:testfx-junit5:4.0.16-alpha")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

application {
    mainClass = "org.example.Main"
    applicationName = "IDEA-330359"
    applicationDefaultJvmArgs = listOf(
        "--add-exports",
        "javafx.base/com.sun.javafx.collections=ALL-UNNAMED",
        "--add-exports",
        "javafx.base/com.sun.javafx.event=ALL-UNNAMED",
        "--add-exports",
        "javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED",
        "--add-exports",
        "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-opens",
        "javafx.base/com.sun.javafx.collections=ALL-UNNAMED",
        "--add-opens",
        "javafx.controls/javafx.scene.control=ALL-UNNAMED",
        "--add-opens",
        "javafx.graphics/javafx.scene=ALL-UNNAMED"
    )
}

javafx {
    version = "17"
    modules = listOf("javafx.base", "javafx.controls", "javafx.fxml", "javafx.graphics")
}

checkstyle {
    isIgnoreFailures = true
}

licenseReport {
    // Consider only first-level dependencies
    filters = arrayOf(ExcludeTransitiveDependenciesFilter())
    // Export dependency as JSON
    renderers = arrayOf(JsonReportRenderer("third-party-libraries.json"))
}

tasks {
    test {
        defaultCharacterEncoding = "UTF-8"
        useJUnitPlatform()
    }

    compileTestJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options {
            // Include unofficial javadoc tags (https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDJGIJB)
            (options as StandardJavadocDocletOptions).tags(
                "apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:"
            )
            encoding = "UTF-8"
        }
    }

    processResources {
        from(generateLicenseReport) {
            include("*.json")
        }
    }

    named<JavaExec>("run") {
        doFirst {
            jvmArgs(application.applicationDefaultJvmArgs)
        }
    }

    shadowJar {
        destinationDirectory = file("$buildDir/libs/shadow")
        exclude("META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/versions/9/module-info*")
        mergeServiceFiles()
    }

    register<JavaExec>("buildProductLicense") {
        group = "Distribution"
        description = "Generate product licenses for this application"

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "com.devagent.license.LicenseGeneration"
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    register("finalizeBuild") {
        group = "Release"
        description = "Build process for application release"

        doLast {
            File("${properties("releaseDirectory").get()}/LATEST").writeText(project.version.toString())

            changelog.keepUnreleasedSection = false
            patchChangelog.get().run()

            File("${properties("releaseDirectory").get()}/CHANGELOG.html").writeText(
                "<style>" + File("$rootDir/src/main/assets/css/changelog.css").readText() + "</style>" + changelog.render(
                    Changelog.OutputType.HTML
                )
            )

            changelog.keepUnreleasedSection = true
            patchChangelog.get().run()
        }
    }
}
