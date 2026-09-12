import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("FServerStorageDatabase") {
            packageName.set("com.fserver.core.storage.database")
        }
    }
}

android {
    namespace = "com.fserver.library.storage"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
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

        // This module is the one place allowed to implement the storage SPI - that is its whole
        // job. Opting in per file would be noise.
        optIn.add("com.fserver.core.store.FServerStorageApi")
    }
}

dependencies {
    // `api`, not `implementation`: every repository here hands back `:core` models, and a consumer
    // of this module is by definition also a consumer of `:core`.
    api(projects.core)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}
