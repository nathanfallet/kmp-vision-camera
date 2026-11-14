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

    kover(projects.core)
    kover(projects.compose)
}
