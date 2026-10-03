import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fserver.core"

    compileSdk {
        version = release(37)
    }
    ndkVersion = "30.0.15729638"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
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
    // UniFFI bindings call into libfx_ffi.so through JNA
    implementation(libs.jna) { artifact { type = "aar" } }

    // Tests
    testImplementation(libs.junit)
}

// --- Rust engine: bindings and native libraries (workspace/docs/crates.md) ---------------

/** Runs `cargo xtask <args> <outputDir>` in the Rust workspace. */
abstract class CargoXtaskTask @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {

    /** `workspace/` - the Cargo workspace root. */
    @get:Internal
    abstract val workspaceDir: DirectoryProperty

    /** Rust sources and manifests; any change re-runs the task. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSources: ConfigurableFileCollection

    @get:Input
    abstract val cargo: Property<String>

    @get:Input
    abstract val xtaskArgs: ListProperty<String>

    /** Flag that receives [outputDir], e.g. `--kotlin-out`. */
    @get:Input
    abstract val outputFlag: Property<String>

    /** Extra environment, e.g. `ANDROID_NDK_HOME`. */
    @get:Input
    abstract val environment: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val cargoBin = cargo.get()
        val cargoDir = File(cargoBin).parentFile
        exec.exec {
            workingDir = workspaceDir.get().asFile
            // IDE-launched Gradle often has no ~/.cargo/bin on PATH; cargo-ndk lives there too.
            if (cargoDir != null) {
                environment("PATH", cargoDir.path + File.pathSeparator + System.getenv("PATH"))
            }
            environment(this@CargoXtaskTask.environment.get())
            commandLine(
                listOf(cargoBin, "xtask") + xtaskArgs.get() +
                    listOf(outputFlag.get(), outputDir.get().asFile.path),
            )
        }
    }
}

val rustWorkspace: Directory = rootProject.layout.projectDirectory.dir("../../../workspace")

/** `fserver.cargo` Gradle property, else `~/.cargo/bin/cargo`, else `cargo` from PATH. */
val cargoBinary: String = providers.gradleProperty("fserver.cargo").orNull
    ?: File(System.getProperty("user.home"), ".cargo/bin/cargo").takeIf { it.canExecute() }?.path
    ?: "cargo"

fun CargoXtaskTask.configureRust() {
    group = "rust"
    workspaceDir.set(rustWorkspace)
    rustSources.from(
        rustWorkspace.file("Cargo.toml"),
        rustWorkspace.file("Cargo.lock"),
        rustWorkspace.dir("crates").asFileTree.matching { exclude("**/target/**") },
    )
    cargo.set(cargoBinary)
}

val generateUniffiKotlin = tasks.register<CargoXtaskTask>("generateUniffiKotlin") {
    description = "Generates Kotlin bindings of fx-ffi (cargo xtask bindings)."
    configureRust()
    xtaskArgs.set(listOf("bindings", "--language", "kotlin"))
    outputFlag.set("--kotlin-out")
    outputDir.set(layout.buildDirectory.dir("generated/uniffi/kotlin"))
}

androidComponents {
    val ndkDir = sdkComponents.ndkDirectory

    onVariants { variant ->
        // Generated sources: compileKotlin depends on generateUniffiKotlin automatically.
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateUniffiKotlin, CargoXtaskTask::outputDir)

        val release = variant.buildType == "release"
        val buildJniLibs = tasks.register<CargoXtaskTask>(
            "cargoNdk${variant.name.replaceFirstChar { it.uppercase() }}",
        ) {
            description = "Builds libfx_ffi.so for Android ABIs (cargo xtask android)."
            configureRust()
            xtaskArgs.set(listOf("android") + if (release) listOf("--release") else emptyList())
            outputFlag.set("--out")
            environment.put("ANDROID_NDK_HOME", ndkDir.map { it.asFile.path })
            outputDir.set(layout.buildDirectory.dir("generated/jniLibs/${variant.name}"))
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildJniLibs, CargoXtaskTask::outputDir)
    }
}
