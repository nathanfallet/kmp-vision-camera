import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import javax.inject.Inject
import org.gradle.process.ExecOperations

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
        name.set("core")
        description.set("A high-performance camera library for Kotlin Multiplatform: devices, session, photo, video and frame processing")
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
 * The camera engine. Android is backed by CameraX (+ Camera2 interop), iOS by AVFoundation.
 * It has no Compose dependency: the Compose integration lives in the compose module.
 */
/**
 * Compiles the Objective-C sources of src/iosMain/objc into libVisionCameraNative.a for one iOS target, with Xcode's
 * clang. cinterop (src/nativeInterop/cinterop/VisionCameraNative.def) embeds the library in the target's klib, so
 * apps and other modules link it without any setup.
 */
@CacheableTask
abstract class BuildIosNativeLibraryTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The SDK, `iphoneos` or `iphonesimulator`. */
    @get:Input
    abstract val sdk: Property<String>

    /** The clang target triple, e.g. `arm64-apple-ios15.0-simulator`. */
    @get:Input
    abstract val targetTriple: Property<String>

    @get:Input
    abstract val libraryName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun build() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        val objects = sources.files.filter { it.extension == "m" }.sorted().map { source ->
            output.resolve("${source.nameWithoutExtension}.o").also { objectFile ->
                execOperations.exec {
                    commandLine(
                        "xcrun", "--sdk", sdk.get(), "clang",
                        "-target", targetTriple.get(),
                        "-fobjc-arc",
                        "-fmodules",
                        "-O2",
                        "-Wall",
                        "-Werror",
                        "-c", source.absolutePath,
                        "-o", objectFile.absolutePath,
                    )
                }
            }
        }
        execOperations.exec {
            commandLine("xcrun", "libtool", "-static", "-o", output.resolve("lib${libraryName.get()}.a").absolutePath)
            args(objects.map { it.absolutePath })
        }
    }
}

/** The minimum iOS version of libVisionCameraNative.a: Kotlin/Native's default. */
val iosMinVersion = "15.0"

/** The tasks building libVisionCameraNative.a, by Kotlin target name. */
val buildIosNativeLibraries = mapOf(
    "iosArm64" to ("iphoneos" to "arm64-apple-ios$iosMinVersion"),
    "iosSimulatorArm64" to ("iphonesimulator" to "arm64-apple-ios$iosMinVersion-simulator"),
).mapValues { (targetName, platform) ->
    tasks.register<BuildIosNativeLibraryTask>("buildIosNativeLibrary${targetName.replaceFirstChar { it.uppercase() }}") {
        description = "Compiles libVisionCameraNative.a (Objective-C) for $targetName with Xcode's clang."
        sources.from(fileTree("src/iosMain/objc"))
        sdk.set(platform.first)
        targetTriple.set(platform.second)
        libraryName.set("VisionCameraNative")
        outputDirectory.set(layout.buildDirectory.dir("generated/iosNativeLibraries/$targetName"))
        // Like the iOS cinterop and compilations, which are skipped on other hosts (e.g. the Linux Docs build).
        onlyIf("Xcode is only available on macOS") { System.getProperty("os.name").startsWith("Mac") }
    }
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "me.nathanfallet.visioncamera"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
    // Objective-C delegates and helpers that keep the AVFoundation objects backed by a buffer pool (depth data,
    // synchronized data) out of Kotlin/Native's garbage collector: see VisionCameraNative.def.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val library = buildIosNativeLibraries.getValue(target.name)
        target.compilations.getByName("main").cinterops.create("VisionCameraNative") {
            includeDirs(file("src/iosMain/objc"))
            extraOpts("-libraryPath", library.get().outputDirectory.get().asFile.absolutePath)
            tasks.named(interopProcessingTaskName) { dependsOn(library) }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.guava)
            api(libs.camerax.core)
            implementation(libs.camerax.camera2)
            api(libs.camerax.lifecycle)
            implementation(libs.camerax.video)
            implementation(libs.camerax.view)
            implementation(libs.camerax.extensions)
            implementation(libs.androidx.activity)
            api(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.process)
        }
    }
}

/**
 * Compiles the JNI sources of src/androidMain/cpp into libVisionCamera.so for every Android ABI, with the NDK's clang.
 *
 * The Kotlin Multiplatform Android library plugin has no externalNativeBuild, so the library is built by this task
 * and added to the variant's jniLibs (see androidComponents below), which packages it in the AAR's jni/<abi>/.
 * The sources only use the NDK's C APIs (no C++ standard library), hence -nostdlib++: no libc++_shared.so to ship.
 */
@CacheableTask
abstract class BuildNativeLibrariesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The Android SDK, containing the NDK in ndk/<ndkVersion>. */
    @get:Internal
    abstract val sdkDirectory: DirectoryProperty

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val libraryName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun build() {
        val ndk = sdkDirectory.get().asFile.resolve("ndk/${ndkVersion.get()}")
        if (!ndk.isDirectory) {
            throw GradleException(
                "NDK ${ndkVersion.get()} is not installed in $ndk, install it with: " +
                    "sdkmanager --install \"ndk;${ndkVersion.get()}\"",
            )
        }
        val prebuilt = ndk.resolve("toolchains/llvm/prebuilt").listFiles()?.singleOrNull { it.isDirectory }
            ?: throw GradleException("No LLVM toolchain found in the NDK at $ndk")
        val clang = listOf("clang++", "clang++.exe").map { prebuilt.resolve("bin/$it") }.firstOrNull { it.exists() }
            ?: throw GradleException("clang++ not found in $prebuilt")
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val library = "lib${libraryName.get()}.so"
        ABI_TARGETS.forEach { (abi, target) ->
            val abiDirectory = output.resolve(abi).apply { mkdirs() }
            execOperations.exec {
                executable = clang.absolutePath
                args(
                    "--target=$target${minSdk.get()}",
                    "-std=c++20",
                    "-O2",
                    "-fPIC",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fstack-protector-all",
                    "-fno-exceptions",
                    "-fno-rtti",
                    "-nostdlib++",
                    "-shared",
                    "-Wl,-soname,$library",
                    // 16 KB page sizes support (Android 15+)
                    "-Wl,-z,max-page-size=16384",
                    "-Wl,--no-undefined",
                    "-Wl,--build-id=sha1",
                    "-o",
                    abiDirectory.resolve(library).absolutePath,
                )
                args(sources.files.filter { it.extension == "cpp" }.sorted().map { it.absolutePath })
                args("-landroid", "-llog")
            }
        }
    }

    private companion object {
        /** The Android ABIs, and their clang target triple (the API level is appended). */
        val ABI_TARGETS = mapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "armeabi-v7a" to "armv7a-linux-androideabi",
            "x86_64" to "x86_64-linux-android",
            "x86" to "i686-linux-android",
        )
    }
}

val buildNativeLibraries = tasks.register<BuildNativeLibrariesTask>("buildNativeLibraries") {
    description = "Compiles libVisionCamera.so (JNI) for every Android ABI with the NDK."
    sources.from(fileTree("src/androidMain/cpp"))
    // Pinned (gradle/libs.versions.toml), since the Kotlin Multiplatform Android library DSL has no ndkVersion.
    sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
    ndkVersion.set(libs.versions.android.ndk)
    minSdk.set(libs.versions.android.minSdk.get().toInt())
    libraryName.set("VisionCamera")
    outputDirectory.set(layout.buildDirectory.dir("generated/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        val jniLibs = variant.sources.jniLibs
            ?: throw GradleException("The ${variant.name} variant has no jniLibs sources to add libVisionCamera.so to")
        jniLibs.addGeneratedSourceDirectory(buildNativeLibraries, BuildNativeLibrariesTask::outputDirectory)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootProject.projectDir}/detekt.yml")
    source.from(file("src/commonMain/kotlin"), file("src/androidMain/kotlin"), file("src/iosMain/kotlin"))
}
