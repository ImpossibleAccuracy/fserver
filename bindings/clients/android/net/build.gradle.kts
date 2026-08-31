import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain JVM, deliberately: :net must stay platform-neutral. Anything that needs a Context
// belongs in a transport module implementing the SPI, not here.
kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Exceptions and shared helpers live here, and they surface in this module's own API.
    api(projects.common)

    // Every seam is a suspend fun or a Flow, so this is api, not implementation.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
