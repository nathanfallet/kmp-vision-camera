plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.maven) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

allprojects {
    group = "me.nathanfallet.visioncamera"
    version = "0.1.0-SNAPSHOT"
    project.ext.set("url", "https://github.com/nathanfallet/kmp-vision-camera")
    project.ext.set("license.name", "Apache 2.0")
    project.ext.set("license.url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
    project.ext.set("developer.id", "nathanfallet")
    project.ext.set("developer.name", "Nathan Fallet")
    project.ext.set("developer.email", "contact@nathanfallet.me")
    project.ext.set("developer.url", "https://www.nathanfallet.me")
    project.ext.set("scm.url", "https://github.com/nathanfallet/kmp-vision-camera.git")
}

dependencies {
    dokka(projects.core)
    dokka(projects.compose)
    dokka(projects.barcode)
    dokka(projects.barcodeCompose)
    dokka(projects.location)
    dokka(projects.locationCompose)

    kover(projects.core)
    kover(projects.compose)
    kover(projects.barcode)
    kover(projects.barcodeCompose)
    kover(projects.location)
    kover(projects.locationCompose)
}

// core's iOS code uses an Objective-C cinterop library (see core/build.gradle.kts), which needs Xcode: on other hosts
// (e.g. the Linux Android CI), Kotlin skips core's iOS compilations, so the iOS code depending on it is skipped too.
if (!System.getProperty("os.name").startsWith("Mac")) {
    val iosMetadataCompilation = Regex("compile(Ios|Apple|Native)MainKotlinMetadata")
    subprojects {
        tasks.configureEach {
            if (iosMetadataCompilation.matches(name)) enabled = false
        }
    }
}
