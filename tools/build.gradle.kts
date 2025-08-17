plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

group = "org.basedai.tools"
version = "unspecified"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:0.3.0")

    // Add the serialization runtime (JSON format)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")


    //test implementations
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}