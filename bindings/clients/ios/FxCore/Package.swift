// swift-tools-version:5.9
//
// Swift wrapper over the Rust engine (`workspace/crates/fx-ffi`).
// Generated pieces, produced from `workspace/`:
//   cargo xtask bindings   -> Sources/FxCore/Generated/FxCore.swift, Generated/Headers/
//   cargo xtask ios        -> FxCoreFFI.xcframework (macOS only)
import PackageDescription

let package = Package(
    name: "FxCore",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "FxCore", targets: ["FxCore"]),
    ],
    targets: [
        .binaryTarget(name: "FxCoreFFI", path: "FxCoreFFI.xcframework"),
        .target(name: "FxCore", dependencies: ["FxCoreFFI"], path: "Sources/FxCore"),
    ]
)
