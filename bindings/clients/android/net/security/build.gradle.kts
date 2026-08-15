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
    api(projects.net)

    api(libs.kotlinx.coroutines.core)

    // CryptoProvider primitives
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.spake2.java)
    implementation(libs.cryptography.core)
    implementation(libs.cryptography.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
