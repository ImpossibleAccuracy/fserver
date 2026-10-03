//! Build automation for the workspace: `cargo xtask <command>`.
//!
//! Commands:
//! - `bindings [--language kotlin|swift] [--kotlin-out DIR] [--swift-out DIR]` - builds `fx-ffi` for the host and
//!   generates Kotlin and Swift bindings from it (UniFFI library mode).
//! - `android [--release] [--out DIR]` - builds `fx-ffi` for Android ABIs with cargo-ndk.
//! - `ios [--release]` - builds `fx-ffi` for iOS and packs an XCFramework (macOS only).

use std::env;
use std::ffi::OsStr;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitCode};

type Result<T> = std::result::Result<T, String>;

/// Crate whose cdylib carries the UniFFI metadata.
const FFI_CRATE: &str = "fx-ffi";
/// Library name of `fx-ffi` (`[lib] name`).
const FFI_LIB: &str = "fx_ffi";
/// Swift module names, must match `crates/fx-ffi/uniffi.toml`.
const SWIFT_MODULE: &str = "FxCore";
const SWIFT_FFI_MODULE: &str = "FxCoreFFI";

/// Android ABIs built by `cargo xtask android`, as cargo-ndk names them.
const ANDROID_ABIS: &[&str] = &["arm64-v8a", "armeabi-v7a", "x86_64"];
/// Rust targets built by `cargo xtask ios`.
const IOS_DEVICE_TARGET: &str = "aarch64-apple-ios";
const IOS_SIM_TARGETS: &[&str] = &["aarch64-apple-ios-sim", "x86_64-apple-ios"];

fn main() -> ExitCode {
    let args: Vec<String> = env::args().skip(1).collect();
    let result = match args.first().map(String::as_str) {
        Some("bindings") => bindings(&args[1..]),
        Some("android") => android(&args[1..]),
        Some("ios") => ios(&args[1..]),
        _ => {
            eprintln!(
                "usage: cargo xtask <command>\n\n\
                 commands:\n  \
                 bindings [--language kotlin|swift] [--kotlin-out DIR] [--swift-out DIR]\n                                                  generate Kotlin and/or Swift bindings\n  \
                 android  [--release] [--out DIR]                build jniLibs with cargo-ndk\n  \
                 ios      [--release]                            build FxCoreFFI.xcframework (macOS)"
            );
            return ExitCode::from(2);
        }
    };
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(err) => {
            eprintln!("xtask: {err}");
            ExitCode::FAILURE
        }
    }
}

// --- commands ---------------------------------------------------------------

fn bindings(args: &[String]) -> Result<()> {
    let root = repo_root()?;
    let (kotlin, swift) = match flag_value(args, "--language") {
        None => (true, true),
        Some("kotlin") => (true, false),
        Some("swift") => (false, true),
        Some(other) => return Err(format!("unknown language `{other}`; use kotlin or swift")),
    };

    run(cargo().args(["build", "--package", FFI_CRATE]))?;
    let library = target_dir()?.join("debug").join(host_library_name());

    if kotlin {
        let out = flag_value(args, "--kotlin-out")
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                root.join("bindings/clients/android/core/build/generated/uniffi/kotlin")
            });
        // The whole output is generated code.
        reset_dir(&out)?;
        generate(&library, "kotlin", &out)?;
        println!("kotlin -> {}", out.display());
    }
    if swift {
        let out = flag_value(args, "--swift-out")
            .map(PathBuf::from)
            .unwrap_or_else(|| root.join("bindings/clients/ios/FxCore"));
        generate_swift(&library, &out)?;
    }
    Ok(())
}

/// `FxCore.swift` goes into the package sources; the C header and module map go to
/// `Generated/Headers`, to be packed into the XCFramework by `cargo xtask ios`.
fn generate_swift(library: &Path, package: &Path) -> Result<()> {
    let tmp = target_dir()?.join("uniffi/swift");
    reset_dir(&tmp)?;
    generate(library, "swift", &tmp)?;

    let sources = package.join("Sources").join(SWIFT_MODULE).join("Generated");
    let headers = package.join("Generated/Headers");
    reset_dir(&sources)?;
    reset_dir(&headers)?;
    copy(
        &tmp.join(format!("{SWIFT_MODULE}.swift")),
        &sources.join(format!("{SWIFT_MODULE}.swift")),
    )?;
    copy(
        &tmp.join(format!("{SWIFT_FFI_MODULE}.h")),
        &headers.join(format!("{SWIFT_FFI_MODULE}.h")),
    )?;
    // Inside an XCFramework the module map must be called `module.modulemap`.
    copy(
        &tmp.join(format!("{SWIFT_FFI_MODULE}.modulemap")),
        &headers.join("module.modulemap"),
    )?;
    println!("swift  -> {}, {}", sources.display(), headers.display());
    Ok(())
}

fn android(args: &[String]) -> Result<()> {
    let root = repo_root()?;
    let out = flag_value(args, "--out")
        .map(PathBuf::from)
        .unwrap_or_else(|| root.join("bindings/clients/android/core/build/generated/jniLibs"));

    if Command::new("cargo-ndk").arg("--version").output().is_err() {
        return Err("cargo-ndk not found; install it with `cargo install cargo-ndk`".into());
    }
    if env::var_os("ANDROID_NDK_HOME").is_none() && env::var_os("ANDROID_HOME").is_none() {
        return Err("set ANDROID_NDK_HOME (or ANDROID_HOME with an installed NDK)".into());
    }

    let mut cmd = cargo();
    cmd.arg("ndk");
    for abi in ANDROID_ABIS {
        cmd.args(["--target", abi]);
    }
    cmd.arg("--output-dir")
        .arg(&out)
        .args(["build", "--package", FFI_CRATE]);
    if has_flag(args, "--release") {
        cmd.arg("--release");
    }
    run(&mut cmd)?;
    println!("jniLibs -> {}", out.display());
    Ok(())
}

fn ios(args: &[String]) -> Result<()> {
    if env::consts::OS != "macos" {
        return Err(format!(
            "iOS builds need macOS with Xcode. On a Mac, add the targets once:\n  \
             rustup target add {IOS_DEVICE_TARGET} {}",
            IOS_SIM_TARGETS.join(" ")
        ));
    }
    let root = repo_root()?;
    let package = root.join("bindings/clients/ios/FxCore");
    let headers = package.join("Generated/Headers");
    if !headers.join("module.modulemap").exists() {
        return Err("headers missing; run `cargo xtask bindings` first".into());
    }

    let profile = if has_flag(args, "--release") {
        "release"
    } else {
        "debug"
    };
    let target = target_dir()?;
    let static_lib = format!("lib{FFI_LIB}.a");

    for t in std::iter::once(&IOS_DEVICE_TARGET).chain(IOS_SIM_TARGETS) {
        let mut cmd = cargo();
        cmd.args(["build", "--package", FFI_CRATE, "--target", t]);
        if profile == "release" {
            cmd.arg("--release");
        }
        run(&mut cmd)?;
    }

    // One fat simulator library (arm64 + x86_64).
    let sim_dir = target.join("ios-sim").join(profile);
    fs::create_dir_all(&sim_dir).map_err(|e| format!("{}: {e}", sim_dir.display()))?;
    let sim_lib = sim_dir.join(&static_lib);
    let mut lipo = Command::new("lipo");
    lipo.arg("-create");
    for t in IOS_SIM_TARGETS {
        lipo.arg(target.join(t).join(profile).join(&static_lib));
    }
    run(lipo.arg("-output").arg(&sim_lib))?;

    let xcframework = package.join(format!("{SWIFT_FFI_MODULE}.xcframework"));
    if xcframework.exists() {
        fs::remove_dir_all(&xcframework).map_err(|e| format!("{}: {e}", xcframework.display()))?;
    }
    run(Command::new("xcodebuild")
        .arg("-create-xcframework")
        .arg("-library")
        .arg(
            target
                .join(IOS_DEVICE_TARGET)
                .join(profile)
                .join(&static_lib),
        )
        .arg("-headers")
        .arg(&headers)
        .arg("-library")
        .arg(&sim_lib)
        .arg("-headers")
        .arg(&headers)
        .arg("-output")
        .arg(&xcframework))?;
    println!("xcframework -> {}", xcframework.display());
    Ok(())
}

// --- helpers ----------------------------------------------------------------

fn generate(library: &Path, language: &str, out: &Path) -> Result<()> {
    run(cargo()
        .args([
            "run",
            "--quiet",
            "--package",
            "uniffi-bindgen",
            "--",
            "generate",
        ])
        .arg(library)
        .args(["--language", language, "--no-format", "--out-dir"])
        .arg(out))
}

fn cargo() -> Command {
    let mut cmd = Command::new(env::var_os("CARGO").unwrap_or_else(|| "cargo".into()));
    cmd.current_dir(workspace_root());
    cmd
}

fn run(cmd: &mut Command) -> Result<()> {
    let status = cmd
        .status()
        .map_err(|e| format!("failed to start {:?}: {e}", cmd.get_program()))?;
    if status.success() {
        Ok(())
    } else {
        let args: Vec<&OsStr> = cmd.get_args().collect();
        Err(format!(
            "{:?} {args:?} exited with {status}",
            cmd.get_program()
        ))
    }
}

/// `workspace/`, the Cargo workspace root.
fn workspace_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("..")
}

/// Repository root, the parent of `workspace/`.
fn repo_root() -> Result<PathBuf> {
    let root = workspace_root().join("..");
    root.canonicalize()
        .map_err(|e| format!("{}: {e}", root.display()))
}

fn target_dir() -> Result<PathBuf> {
    match env::var_os("CARGO_TARGET_DIR") {
        Some(dir) => Ok(PathBuf::from(dir)),
        None => {
            let dir = workspace_root().join("target");
            fs::create_dir_all(&dir).map_err(|e| format!("{}: {e}", dir.display()))?;
            dir.canonicalize()
                .map_err(|e| format!("{}: {e}", dir.display()))
        }
    }
}

fn host_library_name() -> String {
    format!(
        "{}{FFI_LIB}{}",
        env::consts::DLL_PREFIX,
        env::consts::DLL_SUFFIX
    )
}

fn reset_dir(dir: &Path) -> Result<()> {
    if dir.exists() {
        fs::remove_dir_all(dir).map_err(|e| format!("{}: {e}", dir.display()))?;
    }
    fs::create_dir_all(dir).map_err(|e| format!("{}: {e}", dir.display()))
}

fn copy(from: &Path, to: &Path) -> Result<()> {
    fs::copy(from, to)
        .map(drop)
        .map_err(|e| format!("copy {} -> {}: {e}", from.display(), to.display()))
}

fn flag_value<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    args.iter()
        .position(|a| a == flag)
        .and_then(|i| args.get(i + 1))
        .map(String::as_str)
}

fn has_flag(args: &[String], flag: &str) -> bool {
    args.iter().any(|a| a == flag)
}
