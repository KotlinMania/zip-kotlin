// swift-tools-version: 6.0
import PackageDescription
import Foundation

let exportPackagePath = ProcessInfo.processInfo.environment["KOTLIN_SWIFT_EXPORT_PACKAGE"] ?? "../build/SPMPackage/macosArm64/Debug"
let exportArchivePath = ProcessInfo.processInfo.environment["KOTLIN_SWIFT_EXPORT_ARCHIVE"] ?? "../build/swift-test"

let package = Package(
    name: "SwiftTestHarness",
    platforms: [
        .macOS(.v15),
    ],
    dependencies: [
        .package(name: "Zip", path: exportPackagePath)
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "ZipLibrary", package: "Zip")
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-L", exportArchivePath,
                    "-lZip",
                ]),
            ]
        ),
    ],
    swiftLanguageModes: [.v6]
)
