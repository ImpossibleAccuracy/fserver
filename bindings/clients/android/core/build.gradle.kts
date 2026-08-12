import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fserver.library"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        // Only ship ABIs we cross-compile the Rust .so for (see generators/kotlin.sh).
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(projects.net) // TODO: core shouldnt expose net
    implementation(projects.net.transport.android)

    // JNA loads libnetwork_core.so and marshals calls across the FFI.
    // The @aar variant is required on Android: the plain jar ships JNA's own
    // native dispatch lib for desktop platforms only.
    api(libs.jna) {
        artifact {
            name = "jna"
            type = "aar"
        }
    }

    // @RequiresApi, referenced by uniffi's Android object cleaner
    implementation(libs.androidx.annotation)

    // Coroutines — every repository seam is a suspend fun or a Flow, so this is api, not impl.
    api(libs.kotlinx.coroutines.android)

    // Dependency injection — internal wiring only. `FServerCore` runs a standalone Koin container
    // instead of joining the host's, so Koin stays off the consumer's compile classpath and a
    // consuming UI is free to use Hilt, hand-wiring, or its own Koin graph.
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)

    // Nearby Connections — device discovery/transport without a shared network
    implementation(libs.play.services.nearby)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Serialization — the payload a scanned connection code carries
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // Tests
    testImplementation(libs.junit)
}

// -------------- CODEGEN TASKS --------------

// This module is heavely depend on workspace/core:
// Kotlin bindings by uniffi-bindgen, the per-ABI .so by cargo-ndk.
// Both are build output, so they live under build/generated/rust and are wired
// into the main source set below — nothing generated is ever committed.
val generatedRustDir: Provider<Directory> = layout.buildDirectory.dir("generated/rust")
val generatedKotlinDir: Provider<Directory> = generatedRustDir.map { it.dir("kotlin") }
val generatedJniLibsDir: Provider<Directory> = generatedRustDir.map { it.dir("jniLibs") }

val generateRustBindings by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates Kotlin bindings and jniLibs from workspace/core."

    val rustCoreDir = layout.projectDirectory.dir("../../../../workspace/core")

    workingDir = rustCoreDir.asFile
    commandLine("bash", "generators/kotlin.sh")

    // The script defaults to these same paths; passing them keeps Gradle the
    // single source of truth for where build output goes.
    environment("UNIFFI_KOTLIN_OUT_DIR", generatedKotlinDir.get().asFile.absolutePath)
    environment("UNIFFI_JNI_LIBS_OUT_DIR", generatedJniLibsDir.get().asFile.absolutePath)

    // Declared so Gradle can skip the cargo round-trip when nothing changed.
    inputs.dir(rustCoreDir.dir("src"))
    inputs.files(
        rustCoreDir.file("Cargo.toml"),
        rustCoreDir.file("Cargo.lock"),
        rustCoreDir.file("uniffi.toml"),
        rustCoreDir.file("generators/kotlin.sh"),
    )
    outputs.dir(generatedKotlinDir)
    outputs.dir(generatedJniLibsDir)
}

androidComponents {
    onVariants { variant ->
        // !! Testing solution
        // If it starts to fail, try to migrate to `addGeneratedSourceDirectory`

        variant.sources.kotlin?.addStaticSourceDirectory(
            generatedKotlinDir.get().asFile.absolutePath
        )

        variant.sources.jniLibs?.addStaticSourceDirectory(
            generatedJniLibsDir.get().asFile.absolutePath
        )
    }
}

// preBuild is the root of every variant's task graph, so this covers both consumers of
// the generated files: compileKotlin (bindings) and mergeNativeLibs (jniLibs).
tasks.named("preBuild") {
    dependsOn(generateRustBindings)
}
