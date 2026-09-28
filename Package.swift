// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/assets/595591667.zip"
let remoteKotlinChecksum = "1a25cef3c57fac7c4635d1b006478cbc5ea88762820606239277e8057b0b2d07"
let packageName = "EudiEtsi1196x2"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]
        ),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        )
        ,
    ]
)