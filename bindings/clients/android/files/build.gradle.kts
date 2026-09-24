import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

// Android library, unlike `:net`: every scan path here goes through a platform API - MediaStore,
// the Storage Access Framework, StorageManager - so there is no Context-free half worth splitting
// out the way `:net` splits its transports.
android {
    namespace = "com.fserver.library.files"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    // Robolectric runs the MediaStore and SAF backends against providers registered by the tests.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ProgressTask and FileSystemException are both in this module's public surface.
    api(projects.common)

    // Every seam is a suspend fun, so this is api, not implementation.
    api(libs.kotlinx.coroutines.android)

    // Uri parsing for the tree scanner
    implementation(libs.androidx.core.ktx)

    // Storage Access Framework tree walking
    implementation(libs.androidx.documentfile)

    // @RequiresApi on the MediaStore path
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
