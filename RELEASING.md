# Releasing

KMP Vision Camera is published to Maven Central under the `me.nathanfallet.visioncamera` group with the
[vanniktech Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/). Every module
listed in the README is published, with the same version, set in the root `build.gradle.kts`.

## Requirements

The publication must run on **macOS**:

- Xcode compiles the Objective-C and Objective-C++ code of `core`, `resizer` and `skia`, which cinterop embeds in
  the iOS klibs. On other hosts, the iOS publications are skipped or incomplete.
- The Android SDK, with the NDK, builds the JNI libraries: `libVisionCameraResizer.so` (Vulkan, with the NDK's
  `glslc`) and `libVisionCameraSkia.so`.

The plugin reads these keys from `~/.gradle/gradle.properties`:

| Key | Purpose |
| --- | --- |
| `mavenCentralUsername` | User token name of the [Central Portal](https://central.sonatype.com/account) |
| `mavenCentralPassword` | User token password of the Central Portal |
| `signing.keyId` | Short ID of the GPG key that signs the artifacts |
| `signing.password` | Passphrase of that key |
| `signing.secretKeyRingFile` | Path to the secret keyring file holding that key |

The `me.nathanfallet` namespace must be verified in the Central Portal.

## Steps

1. Set the version in the root `build.gradle.kts` (`version = "x.y.z"`), and in the installation snippets of the
   README and the guides (`docs/guides/*.md`).
2. Check the build locally (optional, but it catches signing and packaging issues without uploading anything):

   ```bash
   ./gradlew publishToMavenLocal --no-daemon
   ls ~/.m2/repository/me/nathanfallet/visioncamera/
   ```

3. Upload and release:

   ```bash
   ./gradlew publishAndReleaseToMavenCentral --no-daemon
   ```

   Or, to check the deployment in the Central Portal before releasing it:

   ```bash
   ./gradlew publishToMavenCentral --no-daemon
   ```

   then open [Deployments](https://central.sonatype.com/publishing/deployments), check the files, and click
   **Publish** (or **Drop** to discard it).
4. Tag the release and create the GitHub release:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   gh release create v0.1.0 --generate-notes
   ```

The artifacts show up on Maven Central a few minutes after the release, and in the search a few hours later.

## Skiko

The `skia` modules depend on Skiko. Its Android artifact (`org.jetbrains.skiko:skiko-android`) is only published on
JetBrains' repository, not on Maven Central. Maven Central doesn't check dependencies, so the publication works, but
consumers must add JetBrains' repository (see the README and the Skia guide). Keep Skiko on the version used by
Compose Multiplatform (`skiko` in `gradle/libs.versions.toml`).

The `skia` AAR bundles, for `arm64-v8a` and `x86_64` (the ABIs Skiko ships a runtime for), `libVisionCameraSkia.so`
and Skiko's native runtime (`libskiko-android-*.so`, extracted from the `skiko-android-runtime-*` jars at build
time), so apps need no runtime dependency of their own.
