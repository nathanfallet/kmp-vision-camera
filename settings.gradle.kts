rootProject.name = "kmp-vision-camera"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The camera engine (me.nathanfallet.visioncamera): devices, session, outputs, frames. No Compose dependency.
include(":core")

// The optional Compose Multiplatform integration (me.nathanfallet.visioncamera.compose).
include(":compose")

// The optional barcode scanning module (me.nathanfallet.visioncamera.barcode), port of V5's barcode-scanner package.
include(":barcode")

// The optional Compose integration of the barcode module (me.nathanfallet.visioncamera.barcode.compose).
include(":barcode-compose")

// The demo app: shared Compose UI, and its thin Android application (AGP 9 needs the application plugin in its own
// module). The iOS app is the Xcode project in sample-ios. Not published.
include(":sample")
include(":sample-android")
