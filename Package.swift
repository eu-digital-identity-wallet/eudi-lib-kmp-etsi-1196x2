// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/assets/601373830.zip"
let remoteKotlinChecksum = "f8bb43a93e4dcd0555f9791edc75a2817b1d344ad0c3b4cf9550ee09dcbd730c"
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