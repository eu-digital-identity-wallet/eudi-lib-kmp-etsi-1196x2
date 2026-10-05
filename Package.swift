// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/assets/611896293.zip"
let remoteKotlinChecksum = "d6ac3a6bd534643e2ca91697b6668999fdbbb7ee55fa1a382734adfdb19df0ce"
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