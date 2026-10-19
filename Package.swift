// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/assets/655937830.zip"
let remoteKotlinChecksum = "db126733bc234ad727713b3fb6d1995458ef506a4a9ec40d81984220e52c4df7"
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