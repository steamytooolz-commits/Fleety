plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    application
}
repositories { mavenCentral() }
dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.6.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.1")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.6.1")
    implementation("io.ktor:ktor-client-core-jvm:3.6.1")
    implementation("io.ktor:ktor-client-cio-jvm:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test"))
}
kotlin { jvmToolchain(21) }
application { mainClass.set("com.cityflow.server.MainKt") }
tasks.test { useJUnitPlatform() }
