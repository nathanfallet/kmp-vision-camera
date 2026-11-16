import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import java.net.URI
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
        name.set("skia")
        description.set("Skia rendering for KMP Vision Camera: draw Camera Frames as GPU textures with Skiko")
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
 * The optional Skia rendering module (port of V5's react-native-vision-camera-skia), built on Skiko, JetBrains' Kotlin
 * bindings of Skia (the Skia of Compose Multiplatform on iOS).
 *
 * Frames are wrapped as Skia GPU images without copies, by two small native libraries: an Objective-C++ one on iOS
 * (CVPixelBuffer -> Metal textures -> Skia image, against Skia headers downloaded at build time, see skia-headers.txt), and a
 * JNI one on Android (AHardwareBuffer -> EGLImage -> external OpenGL ES texture).
 */

/**
 * Downloads the Skia headers listed in skia-headers.txt (the ones VisionCameraSkia.mm includes, and their includes)
 * from the JetBrains/skia tag skiko is built with, instead of vendoring them in the repository.
 */
@CacheableTask
abstract class DownloadSkiaHeadersTask : DefaultTask() {
    /** The JetBrains/skia tag, e.g. `m144-22f58c9fd4`. */
    @get:Input
    abstract val tag: Property<String>

    /** The header paths, relative to the root of the Skia repository. */
    @get:Input
    abstract val headers: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        (headers.get() + "LICENSE").forEach { path ->
            val url = "https://raw.githubusercontent.com/JetBrains/skia/${tag.get()}/$path"
            val bytes = (1..DOWNLOAD_ATTEMPTS).firstNotNullOfOrNull { attempt ->
                runCatching { URI(url).toURL().openStream().use { it.readBytes() } }
                    .onFailure { logger.warn("Failed to download $url (attempt $attempt): $it") }
                    .getOrNull()
            } ?: throw GradleException("Could not download the Skia header $url")
            output.resolve(path).apply { parentFile.mkdirs() }.writeBytes(bytes)
        }
    }

    private companion object {
        const val DOWNLOAD_ATTEMPTS = 3
    }
}

val downloadSkiaHeaders = tasks.register<DownloadSkiaHeadersTask>("downloadSkiaHeaders") {
    description = "Downloads the Skia headers the iOS native library is compiled against."
    tag.set(libs.versions.skia)
    headers.set(provider { file("skia-headers.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() } })
    outputDirectory.set(layout.buildDirectory.dir("skia-headers"))
    onlyIf("Xcode is only available on macOS") { System.getProperty("os.name").startsWith("Mac") }
}

/**
 * Compiles the Objective-C++ sources of src/iosMain/objc into a static library for one iOS target, with Xcode's
 * clang, against the downloaded Skia headers. cinterop (src/nativeInterop/cinterop/VisionCameraSkia.def) embeds the
 * library in the target's klib; its Skia symbols are resolved at link time from skiko's libskia.a.
 */
@CacheableTask
abstract class BuildIosNativeLibraryTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val skiaHeaders: DirectoryProperty

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
        val objects = sources.files.filter { it.extension == "mm" }.sorted().map { source ->
            output.resolve("${source.nameWithoutExtension}.o").also { objectFile ->
                execOperations.exec {
                    commandLine(
                        "xcrun", "--sdk", sdk.get(), "clang++",
                        "-target", targetTriple.get(),
                        "-std=c++17",
                        "-fobjc-arc",
                        "-fno-exceptions",
                        "-fno-rtti",
                        "-O2",
                        "-Wall",
                        "-Werror",
                        // The configuration skiko's Skia is built with: release, Ganesh, Metal. The Skia types
                        // built on this side (GrBackendTexture, SkYUVAInfo...) must have the same layout.
                        "-DNDEBUG",
                        "-DSK_RELEASE",
                        "-DSK_GANESH",
                        "-DSK_METAL",
                        "-I", skiaHeaders.get().asFile.absolutePath,
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

/** The minimum iOS version of libVisionCameraSkia.a: Kotlin/Native's default. */
val iosMinVersion = "15.0"

/** The tasks building libVisionCameraSkia.a, by Kotlin target name. */
val buildIosNativeLibraries = mapOf(
    "iosArm64" to ("iphoneos" to "arm64-apple-ios$iosMinVersion"),
    "iosSimulatorArm64" to ("iphonesimulator" to "arm64-apple-ios$iosMinVersion-simulator"),
).mapValues { (targetName, platform) ->
    val taskName = "buildIosNativeLibrary${targetName.replaceFirstChar { it.uppercase() }}"
    tasks.register<BuildIosNativeLibraryTask>(taskName) {
        description = "Compiles libVisionCameraSkia.a (Objective-C++) for $targetName with Xcode's clang."
        sources.from(fileTree("src/iosMain/objc"))
        skiaHeaders.set(downloadSkiaHeaders.flatMap { it.outputDirectory })
        sdk.set(platform.first)
        targetTriple.set(platform.second)
        libraryName.set("VisionCameraSkia")
        outputDirectory.set(layout.buildDirectory.dir("generated/iosNativeLibraries/$targetName"))
        // Like the iOS cinterop and compilations, which are skipped on other hosts (e.g. the Linux Android CI).
        onlyIf("Xcode is only available on macOS") { System.getProperty("os.name").startsWith("Mac") }
    }
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "me.nathanfallet.visioncamera.skia"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
        // Instrumented tests of the native paths, run with connectedAndroidDeviceTest (not part of `build`).
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val library = buildIosNativeLibraries.getValue(target.name)
        target.compilations.getByName("main").cinterops.create("VisionCameraSkia") {
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
            api(projects.core)
            api(libs.skiko)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.junit)
        }
    }
}

/**
 * Compiles the JNI sources of src/androidMain/cpp into libVisionCameraSkia.so with the NDK's clang, for the Android
 * ABIs skiko ships a native runtime for (see core's BuildNativeLibrariesTask, which this one mirrors).
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
                args("-landroid", "-lEGL", "-lGLESv2")
            }
        }
    }

    private companion object {
        /** The Android ABIs skiko has a native runtime for, and their clang target triple (+ the API level). */
        val ABI_TARGETS = mapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "x86_64" to "x86_64-linux-android",
        )
    }
}

val buildNativeLibraries = tasks.register<BuildNativeLibrariesTask>("buildNativeLibraries") {
    description = "Compiles libVisionCameraSkia.so (JNI) for every Android ABI supported by skiko, with the NDK."
    sources.from(fileTree("src/androidMain/cpp"))
    // Pinned (gradle/libs.versions.toml), since the Kotlin Multiplatform Android library DSL has no ndkVersion.
    sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
    ndkVersion.set(libs.versions.android.ndk)
    minSdk.set(libs.versions.android.minSdk.get().toInt())
    libraryName.set("VisionCameraSkia")
    outputDirectory.set(layout.buildDirectory.dir("generated/jniLibs"))
}

/**
 * Extracts skiko's Android native runtime (libskiko-android-<arch>.so, shipped at the root of the
 * skiko-android-runtime-<arch> jars) into jni/<abi>/ directories, so that it is packaged in this module's AAR and
 * loaded by skiko's System.loadLibrary without any setup in apps.
 */
@CacheableTask
abstract class ExtractSkikoRuntimeTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun extract() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        runtimeJars.files.forEach { jar ->
            val abi = ABIS.entries.firstOrNull { jar.name.startsWith("skiko-android-runtime-${it.key}-") }?.value
                ?: throw GradleException("Unknown skiko Android runtime: ${jar.name}")
            fileSystemOperations.copy {
                from(archiveOperations.zipTree(jar)) { include("*.so") }
                into(output.resolve(abi))
            }
        }
    }

    private companion object {
        /** The Android ABI of each skiko Android runtime architecture. */
        val ABIS = mapOf("arm64" to "arm64-v8a", "x64" to "x86_64")
    }
}

val skikoAndroidRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    skikoAndroidRuntime(libs.skiko.android.runtime.arm64)
    skikoAndroidRuntime(libs.skiko.android.runtime.x64)
}

val extractSkikoRuntime = tasks.register<ExtractSkikoRuntimeTask>("extractSkikoRuntime") {
    description = "Extracts skiko's Android native runtime into jniLibs directories."
    runtimeJars.from(skikoAndroidRuntime)
    outputDirectory.set(layout.buildDirectory.dir("generated/skikoJniLibs"))
}

androidComponents {
    onVariants { variant ->
        val jniLibs = variant.sources.jniLibs
            ?: throw GradleException("The ${variant.name} variant has no jniLibs sources to add native libraries to")
        jniLibs.addGeneratedSourceDirectory(buildNativeLibraries, BuildNativeLibrariesTask::outputDirectory)
        jniLibs.addGeneratedSourceDirectory(extractSkikoRuntime, ExtractSkikoRuntimeTask::outputDirectory)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootProject.projectDir}/detekt.yml")
    source.from(file("src/commonMain/kotlin"), file("src/androidMain/kotlin"), file("src/iosMain/kotlin"))
}
