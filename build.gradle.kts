import java.util.Base64

plugins {
    java
    `maven-publish`
    signing
}

val publishedArtifactIds = mapOf(
    "jspecify-core" to "jspecify-migration-core",
    "jspecify-rewrite-recipes" to "jspecify-migration-rewrite-recipes",
    "jspecify-cli" to "jml-cli",
    "jspecify-gradle-plugin" to "jspecify-migration-gradle-plugin",
    "jspecify-maven-plugin" to "jspecify-migration-maven-plugin",
)
val projectVersion = providers.gradleProperty("projectVersion")
    .orElse(providers.environmentVariable("JML_PROJECT_VERSION"))
    .orElse("0.1.0-SNAPSHOT")
val centralPortalStagingDirectory = layout.buildDirectory.dir(
    "central-portal/staging/${projectVersion.get()}")
val centralPortalToken = providers.gradleProperty("centralPortalToken")
    .orElse(providers.environmentVariable("CENTRAL_PORTAL_TOKEN"))
    .orElse(providers.gradleProperty("centralPortalUsername")
        .orElse(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
        .zip(providers.gradleProperty("centralPortalPassword")
            .orElse(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))) { username, password ->
            Base64.getEncoder().encodeToString(
                "$username:$password".toByteArray(Charsets.UTF_8))
        })
val centralPortalPublishingType = providers.gradleProperty("centralPortalPublishingType")
    .orElse(providers.environmentVariable("CENTRAL_PORTAL_PUBLISHING_TYPE"))
    .orElse("USER_MANAGED")

allprojects {
    group = "io.github.javamodernizationlabs"
    version = projectVersion.get()
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    val publishedArtifactId = publishedArtifactIds[name] ?: name
    val signingKey = providers.gradleProperty("signingInMemoryKey")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
    val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword")
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))

    extensions.configure<BasePluginExtension> {
        archivesName.set(publishedArtifactId)
    }

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            // Fail the build on any missing or malformed Javadoc on the public API.
            addBooleanOption("Xdoclint:all", true)
            addBooleanOption("Xwerror", true)
        }
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    extensions.configure<org.gradle.api.publish.PublishingExtension> {
        publications {
            if (project.name != "jspecify-gradle-plugin") {
                create("mavenJava", org.gradle.api.publish.maven.MavenPublication::class.java) {
                    from(components["java"])
                    artifactId = publishedArtifactId
                }
            }
            withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
                if (project.name == "jspecify-gradle-plugin" && name == "pluginMaven") {
                    artifactId = publishedArtifactId
                }
                pom {
                    name.set(publishedArtifactId)
                    description.set("JSpecify Migration Kit tooling for Java nullness migration.")
                    url.set("https://github.com/javamodernizationlabs/JSpecify-migration-kit")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/javamodernizationlabs/JSpecify-migration-kit.git")
                        developerConnection.set("scm:git:ssh://github.com/javamodernizationlabs/JSpecify-migration-kit.git")
                        url.set("https://github.com/javamodernizationlabs/JSpecify-migration-kit")
                    }
                    developers {
                        developer {
                            id.set("java-modernization-labs")
                            name.set("Java Modernization Labs contributors")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "centralPortalStaging"
                url = centralPortalStagingDirectory.get().asFile.toURI()
            }
        }
    }

    extensions.configure<org.gradle.plugins.signing.SigningExtension> {
        isRequired = providers.gradleProperty("release")
            .map(String::toBoolean)
            .orElse(!version.toString().endsWith("SNAPSHOT"))
            .get()
        if (signingKey.isPresent && signingPassword.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        }
        sign(extensions.getByType<org.gradle.api.publish.PublishingExtension>().publications)
    }
}

val centralPortalPublicationTasks = subprojects.map {
    it.tasks.named("publishAllPublicationsToCentralPortalStagingRepository")
}
val mavenLocalPublicationTasks = subprojects.map {
    it.tasks.named("publishToMavenLocal")
}

tasks.named("publishToMavenLocal") {
    dependsOn(mavenLocalPublicationTasks)
}

val centralPortalBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Builds a Maven Central Portal deployment bundle."
    dependsOn(centralPortalPublicationTasks)
    archiveFileName.set("central-portal-bundle-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central-portal"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(centralPortalStagingDirectory)
}

tasks.register<Exec>("publishCentralPortal") {
    group = "publishing"
    description = "Uploads the Central Portal deployment bundle with the Publisher API."
    dependsOn(centralPortalBundle)
    inputs.file(centralPortalBundle.flatMap { it.archiveFile })
    doFirst {
        val token = centralPortalToken.orNull
            ?: throw GradleException("Set CENTRAL_PORTAL_TOKEN or CENTRAL_PORTAL_USERNAME/"
                + "CENTRAL_PORTAL_PASSWORD before publishing to Maven Central.")
        commandLine(
            "curl",
            "--fail",
            "--silent",
            "--show-error",
            "--request",
            "POST",
            "--header",
            "Authorization: Bearer $token",
            "--form",
            "bundle=@${centralPortalBundle.get().archiveFile.get().asFile.absolutePath}",
            "https://central.sonatype.com/api/v1/publisher/upload"
                + "?publishingType=${centralPortalPublishingType.get()}"
        )
    }
}

tasks.register("licenseCheck") {
    group = "verification"
    description = "Checks OSS metadata required for release publication."
    inputs.files("LICENSE", "NOTICE", "README.md", "SECURITY.md", "CONTRIBUTING.md")
    doLast {
        val required = listOf("LICENSE", "NOTICE", "README.md", "SECURITY.md", "CONTRIBUTING.md")
        val missing = required.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) {
            "Missing required OSS metadata files: ${missing.joinToString(", ")}"
        }
        check(layout.projectDirectory.file("LICENSE").asFile.readText()
            .contains("Apache License")) {
            "LICENSE must contain Apache License text."
        }
        check(layout.projectDirectory.file("NOTICE").asFile.readText()
            .contains("JSpecify Migration Kit")) {
            "NOTICE must identify the project."
        }
    }
}

tasks.register("reproducibleBuildCheck") {
    group = "verification"
    description = "Verifies archive tasks use reproducible ordering and timestamps."
    dependsOn(subprojects.map { it.tasks.named("jar") })
    doLast {
        subprojects.forEach { project ->
            project.tasks.withType<Jar>().forEach { jar ->
                check(!jar.isPreserveFileTimestamps) {
                    "${project.path}:${jar.name} preserves file timestamps."
                }
                check(jar.isReproducibleFileOrder) {
                    "${project.path}:${jar.name} does not use reproducible file order."
                }
            }
        }
    }
}

tasks.register("dependencyVulnerabilityCheck") {
    group = "verification"
    description = "Ensures dependency security scanning is wired in GitHub Actions."
    inputs.file(".github/workflows/security.yml")
    doLast {
        val workflow = layout.projectDirectory.file(".github/workflows/security.yml").asFile
        check(workflow.isFile) {
            "Missing .github/workflows/security.yml for dependency and CodeQL scanning."
        }
        val text = workflow.readText()
        check(text.contains("actions/dependency-review-action")) {
            "security.yml must run GitHub dependency review."
        }
        check(text.contains("github/codeql-action/init")) {
            "security.yml must initialize CodeQL."
        }
    }
}

tasks.register("signingConfigurationCheck") {
    group = "verification"
    description = "Verifies release signing, provenance and Central Portal wiring are present."
    inputs.files("build.gradle.kts", ".github/workflows/release.yml")
    doLast {
        val build = layout.projectDirectory.file("build.gradle.kts").asFile.readText()
        check(build.contains("useInMemoryPgpKeys")) {
            "Gradle signing must use in-memory PGP keys for CI releases."
        }
        check(build.contains("signingInMemoryKey")) {
            "Gradle signing must support signingInMemoryKey properties."
        }
        check(build.contains("centralPortalBundle")
            && build.contains("/api/v1/publisher/upload")) {
            "Gradle release wiring must build and upload a Central Portal bundle."
        }
        val release = layout.projectDirectory.file(".github/workflows/release.yml").asFile
        check(release.isFile) {
            "Missing release workflow."
        }
        val workflow = release.readText()
        check(workflow.contains("ORG_GRADLE_PROJECT_signingInMemoryKey")) {
            "Release workflow must pass signing key to Gradle."
        }
        check(workflow.contains("provenance.json")) {
            "Release workflow must publish provenance metadata."
        }
        check(workflow.contains("CENTRAL_PORTAL_TOKEN")
            && workflow.contains("publishCentralPortal")) {
            "Release workflow must upload artifacts to the Central Portal Publisher API."
        }
        check(workflow.contains("GRADLE_PUBLISH_KEY")
            && workflow.contains(":jspecify-gradle-plugin:publishPlugins")) {
            "Release workflow must publish the Gradle plugin to the Plugin Portal."
        }
    }
}

tasks.register("jmhSmoke") {
    group = "verification"
    description = "Runs the JMH smoke benchmark suite."
    dependsOn(":jspecify-core:jmhSmoke")
}

val mavenExecutable = providers.environmentVariable("MAVEN_EXECUTABLE")
    .orElse(providers.provider {
        listOf("/opt/homebrew/bin/mvn", "/usr/local/bin/mvn", "mvn")
            .firstOrNull { file(it).canExecute() } ?: "mvn"
    })

tasks.register("mavenInvokerSmoke") {
    group = "verification"
    description = "Runs a Maven CLI smoke test against the published Maven plugin."
    dependsOn("publishToMavenLocal")
    inputs.property("projectVersion", project.version.toString())
    outputs.upToDateWhen { false }
    doLast {
        val smokeDir = layout.buildDirectory.dir("maven-invoker/jspecify-plan").get().asFile
        delete(smokeDir)
        val sourceDir = smokeDir.resolve("src/main/java/com/acme")
        check(sourceDir.mkdirs()) {
            "Unable to create Maven smoke source directory: $sourceDir"
        }
        smokeDir.resolve("pom.xml").writeText("""
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>jspecify-maven-smoke</artifactId>
              <version>1.0.0</version>
            </project>
            """.trimIndent() + "\n", Charsets.UTF_8)
        sourceDir.resolve("Api.java").writeText("""
            package com.acme;

            import org.jetbrains.annotations.Nullable;

            public class Api {
                @Nullable
                public String name() { return null; }
            }
            """.trimIndent() + "\n", Charsets.UTF_8)

        providers.exec {
            workingDir = smokeDir
            commandLine(mavenExecutable.get(), "-q",
                "io.github.javamodernizationlabs:"
                    + "jspecify-migration-maven-plugin:${project.version}:plan")
        }.result.get().assertNormalExitValue()

        val report = smokeDir.resolve("target/reports/jml/jspecify/plan.md")
        check(report.isFile) {
            "Maven smoke did not write expected report: $report"
        }
        check(report.readText(Charsets.UTF_8).contains("org.jetbrains.annotations.Nullable")) {
            "Maven smoke report did not include the expected annotation inventory."
        }
    }
}

tasks.register("releaseQualityGate") {
    group = "verification"
    description = "Runs the local release quality gates documented for JSpecify Migration Kit."
    dependsOn("build", "licenseCheck", "reproducibleBuildCheck",
        "dependencyVulnerabilityCheck", "signingConfigurationCheck",
        "jmhSmoke", "mavenInvokerSmoke")
}
