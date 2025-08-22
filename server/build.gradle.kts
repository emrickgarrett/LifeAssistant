plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"

    application
}

group = "org.basedai.server"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    // Enforce a single Ktor version across all configurations
    implementation(enforcedPlatform("io.ktor:ktor-bom:3.2.2"))

    // get app implementation to run in server
    implementation(project(":app"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // koog
    implementation("ai.koog:koog-agents:0.3.0") {
        // Prevent transitive Ktor version conflicts (if koog pulls Ktor transitively)
        exclude(group = "io.ktor")
    }


    //server dependencies
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-serialization")
}

tasks.test {
    useJUnitPlatform()
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "org.basedai.server.Server"
}