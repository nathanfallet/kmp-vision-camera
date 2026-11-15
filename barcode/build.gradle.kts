import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven)
}

mavenPublishing {
    // Empty javadoc jars: the full Dokka site is published on visioncamera.nathanfallet.me, and embedding it
    // in every platform publication would blow the Maven Central size limits.
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("barcode")
        description.set("Barcode scanning for KMP Vision Camera: a barcode scanner for frames and images, and a barcode scanner camera output")
        url.set(project.ext.get("url")?.toString())
        licenses {
            license {
                name.set(project.ext.get("license.name")?.toString())
                url.set(project.ext.get("license.url")?.toString())
            }
        }
        developers {
            developer {
                id.set(project.ext.get("developer.id")?.toString())
                name.set(project.ext.get("developer.name")?.toString())
                email.set(project.ext.get("developer.email")?.toString())
                url.set(project.ext.get("developer.url")?.toString())
            }
        }
        scm {
            url.set(project.ext.get("scm.url")?.toString())
        }
    }
}

/**
 * The optional barcode scanning module (port of V5's react-native-vision-camera-barcode-scanner), built on the
 * public native extension points of the core module.
 */
kotlin {
    jvmToolchain(21)
    android {
        namespace = "me.nathanfallet.visioncamera.barcode"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        commonMain.dependencies {
            api(projects.core)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootProject.projectDir}/detekt.yml")
    source.from(file("src/commonMain/kotlin"), file("src/androidMain/kotlin"), file("src/iosMain/kotlin"))
}
