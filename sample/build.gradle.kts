plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

/**
 * The demo: shared Compose UI. Android's application is sample-android, iOS' is the Xcode project in sample-ios.
 * Not published.
 */
kotlin {
    jvmToolchain(21)
    android {
        namespace = "me.nathanfallet.visioncamera.sample"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Sample"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.compose)
            implementation(projects.barcodeCompose)
            implementation(projects.locationCompose)
            implementation(projects.resizerCompose)
            implementation(projects.skiaCompose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
        }
    }
}
