plugins {
    val kotlinVersion = "1.3.72"
    kotlin("jvm") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt:clikt:2.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.7.2")
    implementation("io.github.microutils:kotlin-logging:1.7.9")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.8.0.202006091008-r")
    implementation("org.kohsuke:github-api:1.129")

    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    testImplementation(platform("org.junit:junit-bom:5.6.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testImplementation("org.junit.platform:junit-platform-runner:1.6.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
}

application {
    mainClass.set("io.spinnaker.bumpdeps.MainKt")
}

ktlint {
    enableExperimentalRules.set(true)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
