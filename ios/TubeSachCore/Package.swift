// swift-tools-version:5.9
// Lõi của Tube Sạch cho iOS: gọi InnerTube, đọc dữ liệu YouTube, SponsorBlock.
// Không dùng UIKit/SwiftUI nên build và test được cả trên Linux (CI) lẫn macOS.
import PackageDescription

let package = Package(
    name: "TubeSachCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "TubeSachCore", targets: ["TubeSachCore"]),
    ],
    targets: [
        .target(name: "TubeSachCore"),
        .testTarget(name: "TubeSachCoreTests", dependencies: ["TubeSachCore"]),
    ]
)
