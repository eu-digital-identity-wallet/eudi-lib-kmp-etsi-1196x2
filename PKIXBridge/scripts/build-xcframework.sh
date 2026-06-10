#!/bin/bash
#
# Copyright (c) 2026 European Commission
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Builds PKIXBridge.xcframework for iOS device + iOS Simulator (arm64 + x86_64),
# with zero external dependencies. The xcframework is suitable for consumption
# by Kotlin/Native cinterop and by Swift Package Manager (.binaryTarget).
#
# Output: ${PROJECT_DIR}/build/PKIXBridge.xcframework
#
# IMPORTANT: this script must NOT live inside the build output directory.
# On case-insensitive filesystems (macOS APFS default), `Build/` and `build/`
# collide, so a script under `Build/` would be deleted by `rm -rf build`.
# Hence: this lives at scripts/, output goes to build/.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCES_DIR="$PROJECT_DIR/Sources/PKIXBridge"
BUILD_DIR="$PROJECT_DIR/build"

MODULE_NAME="PKIXBridge"
BUNDLE_ID="eu.europa.ec.eudi.PKIXBridge"
MIN_IOS="13.0"

if [[ "$(uname)" != "Darwin" ]]; then
    echo "ERROR: xcframework production requires macOS (need Xcode)" >&2
    exit 1
fi

if ! command -v xcrun >/dev/null 2>&1; then
    echo "ERROR: xcrun not found; Xcode command-line tools are required" >&2
    exit 1
fi

SOURCE_FILES=$(find "$SOURCES_DIR" -name "*.swift" -type f)
if [[ -z "$SOURCE_FILES" ]]; then
    echo "ERROR: no Swift sources found under $SOURCES_DIR" >&2
    exit 1
fi

# Safety guard: refuse to wipe BUILD_DIR if it would also wipe the script
# (would only happen on case-insensitive FS if BUILD_DIR collides with this
# script's directory). We resolve real paths and compare.
SCRIPT_REAL=$(cd "$SCRIPT_DIR" && pwd -P)
BUILD_REAL_PARENT=$(cd "$(dirname "$BUILD_DIR")" && pwd -P)
if [[ "$SCRIPT_REAL" == "$BUILD_REAL_PARENT"* && "$(basename "$SCRIPT_DIR" | tr '[:upper:]' '[:lower:]')" == "$(basename "$BUILD_DIR" | tr '[:upper:]' '[:lower:]')" ]]; then
    echo "ERROR: script directory collides with BUILD_DIR (case-insensitive FS). Move the script out of '$BUILD_DIR'." >&2
    exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

build_arch() {
    local target=$1
    local module_triple=$2
    local sdk=$3
    local outdir=$4

    mkdir -p "$outdir/swiftmodule"

    local sdkpath
    sdkpath=$(xcrun --sdk "$sdk" --show-sdk-path)

    # shellcheck disable=SC2086
    xcrun -sdk "$sdk" swiftc \
        -module-name "$MODULE_NAME" \
        -emit-library -static \
        -emit-module \
        -emit-module-path "$outdir/swiftmodule/${module_triple}.swiftmodule" \
        -emit-module-interface-path "$outdir/swiftmodule/${module_triple}.swiftinterface" \
        -emit-objc-header \
        -emit-objc-header-path "$outdir/${MODULE_NAME}-Swift.h" \
        -enable-library-evolution \
        -swift-version 5 \
        -target "$target" \
        -sdk "$sdkpath" \
        -O \
        -parse-as-library \
        -o "$outdir/libPKIXBridge.a" \
        $SOURCE_FILES
}

assemble_framework() {
    local libpath=$1
    local swiftmodule_dir=$2
    local objc_header=$3
    local framework_path=$4

    rm -rf "$framework_path"
    mkdir -p "$framework_path/Headers"
    mkdir -p "$framework_path/Modules/${MODULE_NAME}.swiftmodule"

    cp "$libpath" "$framework_path/${MODULE_NAME}"
    cp "$objc_header" "$framework_path/Headers/${MODULE_NAME}-Swift.h"
    cp -R "$swiftmodule_dir/." "$framework_path/Modules/${MODULE_NAME}.swiftmodule/"

    cat > "$framework_path/Modules/module.modulemap" <<EOF
framework module ${MODULE_NAME} {
    umbrella header "${MODULE_NAME}-Swift.h"
    export *
    module * { export * }
}
EOF

    cat > "$framework_path/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>${BUNDLE_ID}</string>
    <key>CFBundleName</key>
    <string>${MODULE_NAME}</string>
    <key>CFBundleExecutable</key>
    <string>${MODULE_NAME}</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>MinimumOSVersion</key>
    <string>${MIN_IOS}</string>
</dict>
</plist>
EOF
}

echo "==> Building iOS device (arm64)..."
build_arch "arm64-apple-ios${MIN_IOS}" "arm64-apple-ios" "iphoneos" "$BUILD_DIR/ios"

echo "==> Building iOS Simulator (arm64)..."
build_arch "arm64-apple-ios${MIN_IOS}-simulator" "arm64-apple-ios-simulator" "iphonesimulator" "$BUILD_DIR/sim-arm64"

echo "==> Building iOS Simulator (x86_64)..."
build_arch "x86_64-apple-ios${MIN_IOS}-simulator" "x86_64-apple-ios-simulator" "iphonesimulator" "$BUILD_DIR/sim-x86_64"

echo "==> Lipo-merging simulator architectures..."
mkdir -p "$BUILD_DIR/sim-fat/swiftmodule"
lipo -create \
    "$BUILD_DIR/sim-arm64/libPKIXBridge.a" \
    "$BUILD_DIR/sim-x86_64/libPKIXBridge.a" \
    -output "$BUILD_DIR/sim-fat/libPKIXBridge.a"
cp -R "$BUILD_DIR/sim-arm64/swiftmodule/." "$BUILD_DIR/sim-fat/swiftmodule/"
cp -R "$BUILD_DIR/sim-x86_64/swiftmodule/." "$BUILD_DIR/sim-fat/swiftmodule/"
cp "$BUILD_DIR/sim-arm64/${MODULE_NAME}-Swift.h" "$BUILD_DIR/sim-fat/${MODULE_NAME}-Swift.h"

echo "==> Assembling iOS device framework..."
assemble_framework \
    "$BUILD_DIR/ios/libPKIXBridge.a" \
    "$BUILD_DIR/ios/swiftmodule" \
    "$BUILD_DIR/ios/${MODULE_NAME}-Swift.h" \
    "$BUILD_DIR/ios/${MODULE_NAME}.framework"

echo "==> Assembling iOS Simulator framework..."
assemble_framework \
    "$BUILD_DIR/sim-fat/libPKIXBridge.a" \
    "$BUILD_DIR/sim-fat/swiftmodule" \
    "$BUILD_DIR/sim-fat/${MODULE_NAME}-Swift.h" \
    "$BUILD_DIR/sim-fat/${MODULE_NAME}.framework"

echo "==> Creating xcframework..."
rm -rf "$BUILD_DIR/${MODULE_NAME}.xcframework"
xcodebuild -create-xcframework \
    -framework "$BUILD_DIR/ios/${MODULE_NAME}.framework" \
    -framework "$BUILD_DIR/sim-fat/${MODULE_NAME}.framework" \
    -output "$BUILD_DIR/${MODULE_NAME}.xcframework" >/dev/null

echo
echo "==> Done: $BUILD_DIR/${MODULE_NAME}.xcframework"
ls "$BUILD_DIR/${MODULE_NAME}.xcframework"
