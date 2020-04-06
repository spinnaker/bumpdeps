plugins {
    val kotlinVersion = "1.3.71"
    kotlin("jvm") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
    application
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("com.github.ajalt:clikt:2.5.0")
    implementation("io.github.microutils:kotlin-logging:1.7.8")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.7.0.202003110725-r")
    implementation("org.kohsuke:github-api:1.109")
    implementation("nu.studer:java-ordered-properties:1.0.2")

    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")
}

application {
    mainClassName = "io.spinnaker.bumpdeps.MainKt"
}

ktlint {
    enableExperimentalRules.set(true)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
